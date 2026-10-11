package com.rentmycar.server.routing

import io.ktor.server.routing.*

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
