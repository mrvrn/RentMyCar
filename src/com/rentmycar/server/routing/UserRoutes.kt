package com.rentmycar.server.routing

import com.rentmycar.domain.user.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun Route.userRoutes(repository: UserRepository) {
    route("/users") {
        get("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()

            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
                return@get
            }

            val user = repository.findById(id)

            if (user == null) {
                call.respond(HttpStatusCode.NotFound, "User not found")
                return@get
            }

            call.respond(user)
        }
    }

    route("/register") {
        post {
            // TODO
        }
    }

    route("/login") {
        post {
            // TODO
        }
    }

    route("/logout") {
        post {
            // TODO
        }
    }
}
