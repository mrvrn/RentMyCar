package com.rentmycar.server.routing

import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.car.InvalidCarLocationException
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.user.UserRole
import com.rentmycar.server.auth.authenticatedUserId
import com.rentmycar.server.auth.authorize
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import com.rentmycar.domain.car.AddCarRequest
import com.rentmycar.domain.car.CarService
import com.rentmycar.domain.car.CarResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

val carRoutesLogger = LoggerFactory.getLogger("CarRoutes")

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

                try {
                    repository.create(input.copy(ownerId = ownerId))
                } catch (_: InvalidCarLocationException) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid pickup or drop-off location")
                }
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
