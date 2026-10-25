package com.rentmycar.server.routing

import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import com.rentmycar.domain.car.DuplicateLicensePlateException
import com.rentmycar.domain.car.CarHasRentalsException
import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.IRentalRepository
import com.rentmycar.domain.rental.RentalNotFoundException
import com.rentmycar.domain.rental.RentalUnavailableException
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.UserRole
import com.rentmycar.server.auth.authorize
import io.ktor.http.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

val carRoutesLogger = LoggerFactory.getLogger("CarRoutes")

fun Route.carRoutes(repository: ICarRepository, rentalRepository: IRentalRepository, userRepository: IUserRepository) {
    route("/cars") {
        get {
            try {
                call.respond(HttpStatusCode.OK, repository.findAll())
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het ophalen van de autos"))
            }
        }

        get("/{id}") {
            val car = call.carOrRespond(repository) ?: return@get
            call.respond(HttpStatusCode.OK, car)
        }

        authorize(UserRole.RENTER) {
            post("/{id}/rental") {
                val renterId = (call.userOrRespond(userRepository) ?: return@post).id
                val carId = (call.carOrRespond(repository) ?: return@post).id

                val input = call.receive<CreateRentalDto>()

                val rental = try {
                    rentalRepository.create(input, renterId, carId)
                } catch (exception: RentalNotFoundException) {
                    return@post call.respond(HttpStatusCode.NotFound, exception.message.orEmpty())
                } catch (exception: RentalUnavailableException) {
                    return@post call.respond(HttpStatusCode.Conflict, exception.message.orEmpty())
                } catch (exception: IllegalArgumentException) {
                    return@post call.respond(HttpStatusCode.BadRequest, exception.message.orEmpty())
                }

                call.respond(HttpStatusCode.Created, rental)
            }
        }

        authorize(UserRole.OWNER) {
            post {
                val ownerId = (call.userOrRespond(userRepository) ?: return@post).id
                val request = try {
                    call.receive<CreateCarDto>()
                } catch (_: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldige JSON"))
                }
                val validationError = when {
                    request.brand.isBlank() -> "Merk mag niet leeg zijn"
                    request.type.isBlank() -> "Type mag niet leeg zijn"
                    request.licensePlate.isBlank() -> "Kenteken mag niet leeg zijn"
                    request.pricePerHour.signum() <= 0 -> "Prijs per uur moet groter zijn dan 0"
                    else -> null
                }
                if (validationError != null) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to validationError))
                }
                try {
                    call.respond(HttpStatusCode.Created, repository.create(request, ownerId))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
                } catch (e: DuplicateLicensePlateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Dit kenteken is al in gebruik"))
                }
            }

            put("/{id}") {
                val ownerId = (call.userOrRespond(userRepository) ?: return@put).id
                val existing = call.carOrRespond(repository) ?: return@put
                if (existing.ownerId != ownerId) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Geen toegang tot deze auto"))
                }
                val request = try {
                    call.receive<UpdateCarDto>()
                } catch (_: BadRequestException) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldige JSON"))
                }
                val validationError = when {
                    request.brand?.isBlank() == true -> "Merk mag niet leeg zijn"
                    request.type?.isBlank() == true -> "Type mag niet leeg zijn"
                    request.licensePlate?.isBlank() == true -> "Kenteken mag niet leeg zijn"
                    request.pricePerHour != null && request.pricePerHour.signum() <= 0 -> "Prijs per uur moet groter zijn dan 0"
                    else -> null
                }
                if (validationError != null) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to validationError))
                }
                try {
                    val updated = repository.update(existing.id, request)
                    if (updated == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "Auto niet gevonden"))
                    else call.respond(HttpStatusCode.OK, updated)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
                } catch (e: DuplicateLicensePlateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Dit kenteken is al in gebruik"))
                }
            }

            delete("/{id}") {
                val ownerId = (call.userOrRespond(userRepository) ?: return@delete).id
                val existing = call.carOrRespond(repository) ?: return@delete
                if (existing.ownerId != ownerId) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Geen toegang tot deze auto"))
                }
                try {
                    call.respond(if (repository.delete(existing.id)) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
                } catch (exception: CarHasRentalsException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to exception.message.orEmpty()))
                }
            }
        }
    }
}
