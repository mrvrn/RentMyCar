package com.rentmycar.server.routing

import com.rentmycar.domain.auth.AuthorizationService
import com.rentmycar.domain.rental.IRentalRepository
import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.user.DuplicateEmailException
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.dto.CreateUserDto
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.rentalRoutes(rentalRepository: IRentalRepository, carRepository: ICarRepository) {
    authenticate("auth-jwt") {
        route("/rentals") {
            post("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val car = carRepository.findById(id) ?: return@post call.respond(HttpStatusCode.NotFound)

                val renterId = call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim("userId")
                    ?.asLong()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)
                val input = call.receive<CreateRentalDto>()
                val rental = rentalRepository.create(input, renterId, car.id)

            }
        }
    }
}