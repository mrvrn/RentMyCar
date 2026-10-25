package com.rentmycar.server.routing

import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import com.rentmycar.domain.car.DuplicateLicensePlateException
import com.rentmycar.domain.car.CarHasRentalsException
import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.auth.AuthorizationService
import com.rentmycar.domain.car.cost.ConsumptionCostResponse
import com.rentmycar.domain.car.cost.TcoResponse
import com.rentmycar.domain.car.cost.toCostModel
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.IRentalRepository
import com.rentmycar.domain.rental.RentalNotFoundException
import com.rentmycar.domain.rental.RentalUnavailableException
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.UserRole
import com.rentmycar.server.auth.authorize
import com.rentmycar.server.auth.authenticatedUserId
import com.rentmycar.server.auth.authenticatedUserRole
import io.ktor.http.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

val carRoutesLogger = LoggerFactory.getLogger("CarRoutes")

fun Route.carRoutes(repository: ICarRepository, rentalRepository: IRentalRepository, userRepository: IUserRepository) {
    val authorizationService = AuthorizationService(userRepository)
    route("/cars") {
        get {
            try {
                call.respond(HttpStatusCode.OK, repository.findAll())
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "An error occurred while retrieving cars"))
            }
        }

        get("/{id}") {
            val car = call.carOrRespond(repository, jsonErrors = true) ?: return@get
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
                if (!authorizationService.canManageCars(call.authenticatedUserRole()!!)) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
                val request = try {
                    call.receive<CreateCarDto>()
                } catch (_: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON"))
                }
                val validationError = when {
                    request.brand.isBlank() -> "Brand must not be blank"
                    request.type.isBlank() -> "Type must not be blank"
                    request.licensePlate.isBlank() -> "License plate must not be blank"
                    request.pricePerHour.signum() <= 0 -> "Hourly rate must be greater than zero"
                    else -> null
                }
                if (validationError != null) {
                    return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to validationError))
                }
                try {
                    call.respond(HttpStatusCode.Created, repository.create(request, ownerId))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid input")))
                } catch (e: DuplicateLicensePlateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "This license plate is already in use"))
                }
            }

            put("/{id}") {
                val ownerId = (call.userOrRespond(userRepository) ?: return@put).id
                val existing = call.carOrRespond(repository, jsonErrors = true) ?: return@put
                if (!authorizationService.canManageCar(ownerId, call.authenticatedUserRole()!!, existing.ownerId)) {
                    return@put call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
                val request = try {
                    call.receive<UpdateCarDto>()
                } catch (_: BadRequestException) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid JSON"))
                }
                val validationError = when {
                    request.brand?.isBlank() == true -> "Brand must not be blank"
                    request.type?.isBlank() == true -> "Type must not be blank"
                    request.licensePlate?.isBlank() == true -> "License plate must not be blank"
                    request.pricePerHour != null && request.pricePerHour.signum() <= 0 -> "Hourly rate must be greater than zero"
                    else -> null
                }
                if (validationError != null) {
                    return@put call.respond(HttpStatusCode.BadRequest, mapOf("error" to validationError))
                }
                try {
                    val updated = repository.update(existing.id, request)
                    if (updated == null) call.respond(HttpStatusCode.NotFound, mapOf("error" to "Car not found"))
                    else call.respond(HttpStatusCode.OK, updated)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Invalid input")))
                } catch (e: DuplicateLicensePlateException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "This license plate is already in use"))
                }
            }

            delete("/{id}") {
                val ownerId = (call.userOrRespond(userRepository) ?: return@delete).id
                val existing = call.carOrRespond(repository, jsonErrors = true) ?: return@delete
                if (!authorizationService.canManageCar(ownerId, call.authenticatedUserRole()!!, existing.ownerId)) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
                try {
                    call.respond(if (repository.delete(existing.id)) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
                } catch (exception: CarHasRentalsException) {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to exception.message.orEmpty()))
                }
            }

            get("/{id}/consumption-cost") {
                if (!authorizationService.canCalculateCost(call.authenticatedUserRole()!!)) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
                val userId = call.authenticatedUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
                val car = call.carOrRespond(repository, jsonErrors = true) ?: return@get
                if (!authorizationService.canManageCar(userId, call.authenticatedUserRole()!!, car.ownerId)) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
                val model = try {
                    car.toCostModel()
                } catch (exception: IllegalArgumentException) {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to (exception.message ?: "Invalid car data")))
                }
                call.respond(
                    HttpStatusCode.OK,
                    ConsumptionCostResponse(car.id, car.category, model.calculateConsumptionCostPerKm())
                )
            }

            get("/{id}/tco") {
                if (!authorizationService.canCalculateCost(call.authenticatedUserRole()!!)) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
                val userId = call.authenticatedUserId()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Authentication required"))
                val car = call.carOrRespond(repository, jsonErrors = true) ?: return@get
                if (!authorizationService.canManageCar(userId, call.authenticatedUserRole()!!, car.ownerId)) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Access denied"))
                }
                val kmPerYear = call.request.queryParameters["kmPerYear"]?.toIntOrNull()?.takeIf { it > 0 }
                    ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "kmPerYear must be a positive integer"))
                val model = try {
                    car.toCostModel()
                } catch (exception: IllegalArgumentException) {
                    return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to (exception.message ?: "Invalid car data")))
                }
                call.respond(
                    HttpStatusCode.OK,
                    TcoResponse(car.id, car.category, kmPerYear, model.calculateTco(kmPerYear))
                )
            }
        }
    }
}
