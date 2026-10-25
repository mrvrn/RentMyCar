package com.rentmycar.server.routing

import com.rentmycar.domain.rental.IRentalRepository
import com.rentmycar.domain.rental.RentalAccessDeniedException
import com.rentmycar.domain.rental.RentalNotFoundException
import com.rentmycar.domain.rental.RentalStatus
import com.rentmycar.domain.rental.RentalUnavailableException
import com.rentmycar.domain.rental.dto.ChangeRentalStatusDto
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.UserRole
import com.rentmycar.server.auth.authorize
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.rentalRoutes(rentalRepository: IRentalRepository, userRepository: IUserRepository) {
    route("/rentals") {
        authorize(UserRole.RENTER) {
            get {
                val user = call.userOrRespond(userRepository) ?: return@get
                call.respond(HttpStatusCode.OK, rentalRepository.findAllByUserId(user.id))
            }

            route("/{id}") {
                get {
                    val user = call.userOrRespond(userRepository) ?: return@get
                    val rental = call.rentalOrRespond(rentalRepository, user.id) ?: return@get
                    call.respond(HttpStatusCode.OK, rental)
                }

                patch("/cancel") {
                    val user = call.userOrRespond(userRepository) ?: return@patch
                    val existing = call.rentalOrRespond(rentalRepository) ?: return@patch

                    val rental = try {
                        rentalRepository.changeStatus(existing.id, RentalStatus.CANCELLED, user.id)
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
        }

        authorize(UserRole.OWNER) {
            patch("/{id}/status") {
                val user = call.userOrRespond(userRepository) ?: return@patch
                val existing = call.rentalOrRespond(rentalRepository) ?: return@patch

                val input = try {
                    call.receive<ChangeRentalStatusDto>()
                } catch (_: BadRequestException) {
                    return@patch call.respond(HttpStatusCode.BadRequest, "Invalid status request")
                }

                if (input.status != RentalStatus.CONFIRMED && input.status != RentalStatus.REJECTED) {
                    return@patch call.respond(HttpStatusCode.BadRequest, "Status must be CONFIRMED or REJECTED")
                }

                val rental = try {
                    rentalRepository.changeStatus(existing.id, input.status, user.id)
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
