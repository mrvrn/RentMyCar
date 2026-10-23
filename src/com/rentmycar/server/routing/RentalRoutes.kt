package com.rentmycar.server.routing

import com.rentmycar.domain.rental.IRentalRepository
import com.rentmycar.domain.rental.RentalAccessDeniedException
import com.rentmycar.domain.rental.RentalStatus
import com.rentmycar.domain.rental.dto.ChangeRentalStatusDto
import io.ktor.server.plugins.BadRequestException
import com.rentmycar.domain.rental.RentalNotFoundException
import com.rentmycar.domain.rental.RentalUnavailableException
import com.rentmycar.domain.rental.dto.CreateRentalDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.rentalRoutes(rentalRepository: IRentalRepository) {
    authenticate("auth-jwt") {
        route("/rentals") {
            patch("/{id}/cancel") {
                val rentalId = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid rental ID")

                val loggedInUserId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asLong()
                    ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                val rental = try {
                    rentalRepository.changeStatus(rentalId, RentalStatus.CANCELLED, loggedInUserId)
                } catch (exception: RentalNotFoundException) {
                    return@patch call.respond(HttpStatusCode.NotFound, exception.message.orEmpty())
                } catch (exception: RentalAccessDeniedException) {
                    return@patch call.respond(HttpStatusCode.Forbidden, exception.message.orEmpty())
                } catch (exception: RentalUnavailableException) {
                    return@patch call.respond(HttpStatusCode.Conflict, exception.message.orEmpty())
                }

                call.respond(HttpStatusCode.OK, rental)
            }

            patch("/{id}/status") {
                val rentalId = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid rental ID")

                val loggedInUserId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asLong()
                    ?: return@patch call.respond(HttpStatusCode.Unauthorized)

                val input = try {
                    call.receive<ChangeRentalStatusDto>()
                } catch (exception: BadRequestException) {
                    return@patch call.respond(HttpStatusCode.BadRequest, "Invalid status request")
                }

                if (input.status != RentalStatus.CONFIRMED && input.status != RentalStatus.REJECTED) {
                    return@patch call.respond(HttpStatusCode.BadRequest, "Status must be CONFIRMED or REJECTED")
                }

                val rental = try {
                    rentalRepository.changeStatus(rentalId, input.status, loggedInUserId)
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

            post("/{id}") {
                val carId = call.parameters["id"]?.toLongOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid car ID")

                val renterId = call.principal<JWTPrincipal>()
                    ?.payload?.getClaim("userId")?.asLong()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

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
