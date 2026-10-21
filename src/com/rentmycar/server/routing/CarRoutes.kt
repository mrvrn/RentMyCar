package com.rentmycar.server.routing

import com.rentmycar.domain.car.ICarRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import com.rentmycar.domain.car.CarRepository
import com.rentmycar.domain.car.DuplicateLicensePlateException
import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

val carRoutesLogger = LoggerFactory.getLogger("CarRoutes")

fun Route.carRoutes(repository: ICarRepositoryrepository: ICarRepository = CarRepository()) {
    route("/cars") {
        get {
            try {
                val cars = repository.findAll()
                call.respond(HttpStatusCode.OK, cars)
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het ophalen van de autos"))
            }
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldig car ID"))
                return@get
            }

            try {
                val car = repository.findById(id)
                if (car == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Auto niet gevonden"))
                } else {
                    call.respond(HttpStatusCode.OK, car)
                }
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het ophalen van de auto"))
            }
        }

        authenticate("auth-jwt") {
            post {
                val ownerId = call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim("userId")
                    ?.asLong() ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val request = try {
                    call.receive<CreateCarDto>()
                } catch (e: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldige JSON"))
                }

                val validationError = when {
                    request.brand.isBlank() -> "Merk mag niet leeg zijn"
                    request.type.isBlank() -> "Type mag niet leeg zijn"
                    request.licensePlate.isBlank() -> "Kenteken mag niet leeg zijn"
                    request.pricePerDay <= 0.0 -> "Prijs per dag moet groter zijn dan 0"
                    else -> null
                }
                if (validationError != null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to validationError))
                    return@post
                }

                try {
                    val created = repository.create(request, ownerId)
                    call.respond(HttpStatusCode.Created, created)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
                } catch (e: DuplicateLicensePlateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Dit kenteken is al in gebruik"))
                } catch (e: Exception) {
                    carRoutesLogger.error("Internal server error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het toevoegen van de auto"))
                }
            }

            put("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldig car ID"))
                    return@put
                }

                val ownerId = call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim("userId")
                    ?.asLong() ?: return@put call.respond(HttpStatusCode.Unauthorized)

                val existing = repository.findById(id)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Auto niet gevonden"))
                    return@put
                }

                if (existing.ownerId != ownerId) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Geen toegang tot deze auto"))
                    return@put
                }

                val request = try {
                    call.receive<UpdateCarDto>()
                } catch (e: BadRequestException) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldige JSON"))
                }

                if (request.pricePerDay != null && request.pricePerDay <= 0.0) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Prijs per dag moet groter zijn dan 0"))
                    return@put
                }

                try {
                    val updated = repository.update(id, request)
                    if (updated == null) {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Auto niet gevonden"))
                    } else {
                        call.respond(HttpStatusCode.OK, updated)
                    }
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
                } catch (e: DuplicateLicensePlateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "Dit kenteken is al in gebruik"))
                } catch (e: Exception) {
                    carRoutesLogger.error("Internal server error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het updaten van de auto"))
                }
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldig car ID"))
                    return@delete
                }

                val ownerId = call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim("userId")
                    ?.asLong() ?: return@delete call.respond(HttpStatusCode.Unauthorized)

                val existing = repository.findById(id)
                if (existing == null) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "Auto niet gevonden"))
                    return@delete
                }

                if (existing.ownerId != ownerId) {
                    call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Geen toegang tot deze auto"))
                    return@delete
                }

                try {
                    val deleted = repository.delete(id)
                    if (deleted) {
                        call.respond(HttpStatusCode.NoContent)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Auto niet gevonden"))
                    }
                } catch (e: Exception) {
                    carRoutesLogger.error("Internal server error: ${e.message}", e)
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het verwijderen van de auto"))
                }
            }
        }
    }
}
