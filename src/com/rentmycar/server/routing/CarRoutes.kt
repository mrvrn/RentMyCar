package com.rentmycar.server.routing

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

fun Route.carRoutes() {
    route("/cars") {
        get {
            try {
                carRoutesLogger.info("Received GET /cars request")
                val cars = CarService.getAllCars()
                carRoutesLogger.info("Fetched ${cars.size} cars")
                call.respond(HttpStatusCode.OK, cars)
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het ophalen van de autos"))
            }
        }
        
        post {
            try {
                carRoutesLogger.info("Received POST /cars request")
                val request = call.receive<AddCarRequest>()
                carRoutesLogger.info("Request body: $request")
                val ownerId = 1L
                carRoutesLogger.info("Using hardcoded ownerId: $ownerId")
                val createdCar = CarService.addCar(request, ownerId)
                carRoutesLogger.info("Car created successfully: $createdCar")
                call.respond(HttpStatusCode.Created, createdCar)
            } catch (e: IllegalArgumentException) {
                carRoutesLogger.error("Validation error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het toevoegen van de auto"))
            }
        }
    }
}
