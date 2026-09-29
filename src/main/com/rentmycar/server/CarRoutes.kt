package main.com.rentmycar.server

import io.ktor.server.routing.*

// server/CustomerRoutes.kt
fun Route.carRoutes() {
    route("/cars") {
        // These are examples
//        post("/{id}/contacts") {
//            val id = call.parameters["id"]!!.toLong()
//            val contact = call.receive<Contact>()
//            val updated = service.addContact(CustomerId(id), contact)
//            call.respond(updated ?: HttpStatusCode.NotFound)
//        }
//
        get("/{id}") {
            println(call.parameters["id"])
        }
    }
}