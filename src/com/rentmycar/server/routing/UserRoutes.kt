package com.rentmycar.server.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.rentmycar.domain.auth.AuthorizationService
import com.rentmycar.domain.user.DuplicateEmailException
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.UserRole
import com.rentmycar.domain.user.dto.LoginUserDto
import com.rentmycar.domain.user.dto.CreateUserDto
import com.rentmycar.domain.user.dto.RegisterUserRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
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

    authenticate("auth-jwt") {
        route("/users") {
            get("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()

                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
                    return@get
                }

                val principal = call.principal<JWTPrincipal>()
                val authenticatedUserId = principal
                    ?.payload
                    ?.getClaim("userId")
                    ?.asLong()

                if (authenticatedUserId != id) {
                    call.respond(HttpStatusCode.Forbidden, "Access denied")
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
    }

    route("/register") {
        post {
            val input = try {
                call.receive<RegisterUserRequest>()
            } catch (_: BadRequestException) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid registration data")
            }
            val user = input.copy(
                name = input.name.trim(),
                email = input.email.trim(),
                phone = input.phone.trim(),
                address = input.address.trim(),
            )
            val createUser = CreateUserDto(
                name = user.name,
                email = user.email,
                phone = user.phone,
                address = user.address,
                password = user.password,
            )

            val error = when {
                listOf(user.name, user.email, user.phone, user.address).any { it.isBlank() } ->
                    "Name, email, phone and address are required"
                listOf(user.name, user.email, user.phone, user.address).any { it.length > 100 } ->
                    "Name, email, phone and address must be at most 100 characters"
                !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(user.email) ->
                    "Invalid email address"
                user.password.length < 8 || user.password.isBlank() ->
                    "Password must contain at least 8 characters"
                user.password.toByteArray(Charsets.UTF_8).size > 72 ->
                    "Password must be at most 72 UTF-8 bytes"
                else -> null
            }

            if (error != null) {
                return@post call.respond(HttpStatusCode.BadRequest, error)
            }

            try {
                // Self-registration always creates a renter; an owner role is never client-assignable.
                repository.create(createUser, UserRole.RENTER)
            } catch (_: DuplicateEmailException) {
                return@post call.respond(HttpStatusCode.Conflict, "Email is already registered")
            }
            call.respond(HttpStatusCode.Created)
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
}
