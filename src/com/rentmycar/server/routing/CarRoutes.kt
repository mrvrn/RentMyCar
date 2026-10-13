package com.rentmycar.server.routing

import com.rentmycar.domain.car.ICarRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import com.rentmycar.domain.car.AddCarRequest
import com.rentmycar.domain.car.CarResponse
import com.rentmycar.domain.car.CarService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.carRoutes(repository: ICarRepository) {
    route("/cars") {
        post {
            try {
                val request = call.receive<AddCarRequest>()
                val ownerId = 5L // TODO: Replace with JWT authentication
                val createdCar = CarService.addCar(request, ownerId)
                call.respond(HttpStatusCode.Created, createdCar)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het toevoegen van de auto"))
            }
        }
    }
}
