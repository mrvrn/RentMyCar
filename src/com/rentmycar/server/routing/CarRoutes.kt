package com.rentmycar.server.routing

import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.rental.IRentalRepository
import com.rentmycar.domain.rental.RentalNotFoundException
import com.rentmycar.domain.rental.RentalUnavailableException
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.user.IUserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlin.text.toLongOrNull

private suspend fun ApplicationCall.carIdOrRespond(carRepository: ICarRepository): Long? {
    val id = parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }

    if (id == null) {
        respond(HttpStatusCode.BadRequest, "Invalid car ID")
        return null
    }

    if (carRepository.findById(id) == null) {
        respond(HttpStatusCode.NotFound, "Car not found")
        return null
    }

    return id
}

fun Route.carRoutes(repository: ICarRepository, rentalRepository: IRentalRepository, userRepository: IUserRepository) {
    route("/cars") {
        route("/{id}") {
            get {
                val id = call.carIdOrRespond(repository) ?: return@get
                val car = repository.findById(id)

                if (car == null) {
                    call.respond(HttpStatusCode.NotFound, "Car not found")
                    return@get
                }

                call.respond(car)
            }

            authenticate("auth-jwt") {
                post("/rental") {
                    val carId = call.carIdOrRespond(repository) ?: return@post
                    val renterId = call.userIdOrRespond(userRepository) ?: return@post

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
        }
    }
}
