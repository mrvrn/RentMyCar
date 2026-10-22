package com.rentmycar.server.routing

import com.rentmycar.domain.rental.IRentalRepository
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
