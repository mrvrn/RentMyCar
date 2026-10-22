package com.rentmycar.server.routing

import com.rentmycar.domain.car.ICarRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.carRoutes(repository: ICarRepository) {
    route("/cars") {
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
