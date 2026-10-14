package com.rentmycar.server.routing

import com.rentmycar.domain.car.ICarRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import com.rentmycar.domain.car.AddCarRequest
import com.rentmycar.domain.car.CarService
import com.rentmycar.domain.car.CarResponse
import com.rentmycar.domain.car.UpdateCarRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.sql.SQLIntegrityConstraintViolationException

val carRoutesLogger = LoggerFactory.getLogger("CarRoutes")

fun Route.carRoutes(repository: ICarRepository) {
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
                // TODO: Replace with authenticated user ID when auth is implemented
                val ownerId = 1L
                carRoutesLogger.info("Using hardcoded ownerId: $ownerId (TODO: use authenticated user)")
                val createdCar = CarService.addCar(request, ownerId)
                carRoutesLogger.info("Car created successfully: $createdCar")
                call.respond(HttpStatusCode.Created, createdCar)
            } catch (e: io.ktor.server.plugins.ContentTransformationException) {
                carRoutesLogger.error("Malformed JSON: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldige JSON"))
            } catch (e: IllegalArgumentException) {
                carRoutesLogger.error("Validation error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
            } catch (e: SQLIntegrityConstraintViolationException) {
                carRoutesLogger.warn("Duplicate license plate: ${e.message}")
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Dit kenteken is al in gebruik"))
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het toevoegen van de auto"))
            }
        }
        
        get("/{id}") {
            try {
                val id = call.parameters["id"]?.toLong() ?: throw IllegalArgumentException("Ongeldig car ID")
                carRoutesLogger.info("Received GET /cars/$id request")
                val car = CarService.getCarById(id)
                call.respond(HttpStatusCode.OK, car)
            } catch (e: IllegalArgumentException) {
                carRoutesLogger.error("Validation error: ${e.message}")
                if (e.message?.contains("bestaat niet") == true) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Auto niet gevonden")))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
                }
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het ophalen van de auto"))
            }
        }
        
        put("/{id}") {
            try {
                val id = call.parameters["id"]?.toLong() ?: throw IllegalArgumentException("Ongeldig car ID")
                // TODO: Add ownership check when auth is implemented
                val request = call.receive<UpdateCarRequest>()
                carRoutesLogger.info("Received PUT /cars/$id request with body: $request")
                val updatedCar = CarService.updateCar(id, request)
                call.respond(HttpStatusCode.OK, updatedCar)
            } catch (e: io.ktor.server.plugins.ContentTransformationException) {
                carRoutesLogger.error("Malformed JSON: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Ongeldige JSON"))
            } catch (e: IllegalArgumentException) {
                carRoutesLogger.error("Validation error: ${e.message}")
                if (e.message?.contains("bestaat niet") == true) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Auto niet gevonden")))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
                }
            } catch (e: SQLIntegrityConstraintViolationException) {
                carRoutesLogger.warn("Duplicate license plate: ${e.message}")
                call.respond(HttpStatusCode.Conflict, mapOf("error" to "Dit kenteken is al in gebruik"))
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het updaten van de auto"))
            }
        }
        
        delete("/{id}") {
            try {
                val id = call.parameters["id"]?.toLong() ?: throw IllegalArgumentException("Ongeldig car ID")
                // TODO: Add ownership check when auth is implemented
                carRoutesLogger.info("Received DELETE /cars/$id request")
                CarService.deleteCar(id)
                call.respond(HttpStatusCode.NoContent)
            } catch (e: IllegalArgumentException) {
                carRoutesLogger.error("Validation error: ${e.message}")
                if (e.message?.contains("bestaat niet") == true) {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to (e.message ?: "Auto niet gevonden")))
                } else {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "Ongeldige input")))
                }
            } catch (e: Exception) {
                carRoutesLogger.error("Internal server error: ${e.message}", e)
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Er is een fout opgetreden bij het verwijderen van de auto"))
            }
        }
    }
}
