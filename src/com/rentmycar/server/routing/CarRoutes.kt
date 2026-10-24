package com.rentmycar.server.routing

import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.user.UserRole
import com.rentmycar.server.auth.authenticatedUserId
import com.rentmycar.server.auth.authorize
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.carRoutes(repository: ICarRepository) {
    route("/cars") {
        authorize(UserRole.OWNER) {
            post {
                val ownerId = call.authenticatedUserId()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                val input = try {
                    call.receive<CreateCarDto>()
                } catch (_: BadRequestException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid car data")
                }

                repository.create(input.copy(ownerId = ownerId))
                call.respond(HttpStatusCode.Created)
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid car ID")
                val ownerId = call.authenticatedUserId()
                    ?: return@delete call.respond(HttpStatusCode.Unauthorized, "Authentication required")
                val car = repository.findById(id)
                    ?: return@delete call.respond(HttpStatusCode.NotFound, "Car not found")

                if (car.ownerId != ownerId) {
                    return@delete call.respond(HttpStatusCode.Forbidden, "You do not own this car")
                }

                repository.delete(id)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        get("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()

            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid car ID")
                return@get
            }

            val car = repository.findById(id)

            if (car == null) {
                call.respond(HttpStatusCode.NotFound, "Car not found")
                return@get
            }

            call.respond(car)
        }
    }
}
