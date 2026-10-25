package com.rentmycar.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.domain.auth.AuthorizationService
import com.rentmycar.domain.user.DuplicateEmailException
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.UserRole
import com.rentmycar.domain.user.UserValidation
import com.rentmycar.domain.user.dto.LoginUserDto
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.domain.user.dto.RegisterUserRequest
import com.rentmycar.server.auth.authenticatedUserId
import com.rentmycar.server.auth.authorize
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import java.util.Date

fun Route.userRoutes(repository: IUserRepository) {
    val authorizationService = AuthorizationService(repository)

    val jwtConfig = environment.config
    val secret = jwtConfig.property("jwt.secret").getString()
    val issuer = jwtConfig.property("jwt.issuer").getString()
    val audience = jwtConfig
        .property("jwt.audience").getString()

    authorize(UserRole.RENTER) {
        get("/users/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
            if (call.authenticatedUserId() != id) {
                return@get call.respond(HttpStatusCode.Forbidden, "Access denied")
            }
            val user = call.userOrRespond(repository) ?: return@get
            call.respond(user)
        }
    }

    route("/register") {
        post {
            val input = try {
                call.receive<RegisterUserRequest>()
            } catch (_: BadRequestException) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid registration data")
            }

            val createUser = try {
                UserValidation.normalizeAndValidate(CreateUserDto(input.name, input.email, input.phone, input.address, input.password))
            } catch (exception: IllegalArgumentException) {
                return@post call.respond(HttpStatusCode.BadRequest, exception.message.orEmpty())
            }

            try {
                // Self-registration always creates a renter; an owner role is never client-assignable.
                repository.create(createUser, UserRole.RENTER)
            } catch (_: DuplicateEmailException) {
                // Do not disclose whether an email address is registered.
            }
            call.respond(HttpStatusCode.OK, "Registration request processed.")
        }
    }

    route("/login") {
        post {
            val credentials = call.receive<LoginUserDto>()
            val user = authorizationService.authenticate(credentials)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid email or password")

            val token = JWT.create()
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("userId", user.id.value)
                .withClaim("role", user.role.name)
                .withExpiresAt(Date(System.currentTimeMillis() + 60 * 60 * 1000)) // 1 hour
                .sign(Algorithm.HMAC256(secret))

            call.respond(mapOf("token" to token, "role" to user.role.name))
        }
    }

    route("/logout") {
        post {
            call.respond(
                HttpStatusCode.NotImplemented,
                "Server-side logout is not implemented. Discard the token on the client; it remains valid until expiry."
            )
        }
    }

    authorize(UserRole.RENTER) {
        get("/users/{id}") {
            val requestedId = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
            val authenticatedId = call.authenticatedUserId()
                ?: return@get call.respond(HttpStatusCode.Unauthorized, "Authentication required")
            if (authenticatedId != requestedId) {
                return@get call.respond(HttpStatusCode.Forbidden, "Access denied")
            }
            val user = repository.findById(requestedId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")
            call.respond(HttpStatusCode.OK, user)
        }
    }
}
