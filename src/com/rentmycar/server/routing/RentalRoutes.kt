package com.rentmycar.server.routing

import com.rentmycar.domain.rental.IRentalRepository
import com.rentmycar.domain.rental.RentalAccessDeniedException
import com.rentmycar.domain.rental.RentalStatus
import com.rentmycar.domain.rental.dto.ChangeRentalStatusDto
import io.ktor.server.plugins.BadRequestException
import com.rentmycar.domain.rental.RentalNotFoundException
import com.rentmycar.domain.rental.RentalUnavailableException
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.user.IUserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

private suspend fun ApplicationCall.rentalOrRespond(
    rentalRepository: IRentalRepository
): RentalDto? { // Use the return type of your repository's findById
    val id = parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }

    if (id == null) {
        respond(HttpStatusCode.BadRequest, "Invalid rental ID")
        return null
    }

    val rental = rentalRepository.findById(id)

    if (rental == null) {
        respond(HttpStatusCode.NotFound, "Rental not found")
        return null
    }

    return rental
}

fun Route.rentalRoutes(rentalRepository: IRentalRepository, userRepository: IUserRepository) {
    authenticate("auth-jwt") {
        route("/rentals") {
            get {
                val loggedInUserId = call.userIdOrRespond(userRepository) ?: return@get
                return@get call.respond(HttpStatusCode.OK, rentalRepository.findAllByUserId(loggedInUserId))
            }

            route("/{id}") {
                get {
                    val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid rental ID")
                    val loggedInUserId = call.userIdOrRespond(userRepository) ?: return@get
                    val rental = try {
                        rentalRepository.findDetailsById(id, loggedInUserId)
                    } catch (exception: RentalAccessDeniedException) {
                        return@get call.respond(HttpStatusCode.Forbidden, exception.message.orEmpty())
                    } ?: return@get call.respond(HttpStatusCode.NotFound, "Rental not found")
                    return@get call.respond(HttpStatusCode.OK, rental)
                }

                patch("/cancel") {
                    val rentalDto = call.rentalOrRespond(rentalRepository) ?: return@patch
                    val loggedInUserId = call.userIdOrRespond(userRepository) ?: return@patch

                    val rental = try {
                        rentalRepository.changeStatus(rentalDto.id, RentalStatus.CANCELLED, loggedInUserId)
                    } catch (exception: RentalNotFoundException) {
                        return@patch call.respond(HttpStatusCode.NotFound, exception.message.orEmpty())
                    } catch (exception: RentalAccessDeniedException) {
                        return@patch call.respond(HttpStatusCode.Forbidden, exception.message.orEmpty())
                    } catch (exception: RentalUnavailableException) {
                        return@patch call.respond(HttpStatusCode.Conflict, exception.message.orEmpty())
                    }

                    call.respond(HttpStatusCode.OK, rental)
                }
            }



            patch("/{id}/status") {
                val rentalDto = call.rentalOrRespond(rentalRepository) ?: return@patch
                val loggedInUserId = call.userIdOrRespond(userRepository) ?: return@patch

                val input = try {
                    call.receive<ChangeRentalStatusDto>()
                } catch (exception: BadRequestException) {
                    return@patch call.respond(HttpStatusCode.BadRequest, "Invalid status request")
                }

                if (input.status != RentalStatus.CONFIRMED && input.status != RentalStatus.REJECTED) {
                    return@patch call.respond(HttpStatusCode.BadRequest, "Status must be CONFIRMED or REJECTED")
                }

                val rental = try {
                    rentalRepository.changeStatus(rentalDto.id, input.status, loggedInUserId)
                } catch (exception: RentalNotFoundException) {
                    return@patch call.respond(HttpStatusCode.NotFound, exception.message.orEmpty())
                } catch (exception: RentalAccessDeniedException) {
                    return@patch call.respond(HttpStatusCode.Forbidden, exception.message.orEmpty())
                } catch (exception: RentalUnavailableException) {
                    return@patch call.respond(HttpStatusCode.Conflict, exception.message.orEmpty())
                } catch (exception: IllegalArgumentException) {
                    return@patch call.respond(HttpStatusCode.BadRequest, exception.message.orEmpty())
                }

                call.respond(HttpStatusCode.OK, rental)
            }
        }
    }
}
