package com.rentmycar.server.auth

import com.rentmycar.domain.user.UserRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun ApplicationCall.authenticatedUserId(): Long? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asLong()

fun ApplicationCall.authenticatedUserRole(): UserRole? =
    UserRole.fromString(principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString())

class RoleAuthorizationConfig {
    var allowedRoles: Set<UserRole> = emptySet()
}

val RoleAuthorizationPlugin = createRouteScopedPlugin(
    name = "RoleAuthorization",
    createConfiguration = ::RoleAuthorizationConfig
) {
    val allowedRoles = pluginConfig.allowedRoles
    on(AuthenticationChecked) { call ->
        if (call.isHandled) return@on

        if (call.principal<JWTPrincipal>() == null) {
            call.respond(HttpStatusCode.Unauthorized, "Authentication required")
            return@on
        }

        val userRole = call.authenticatedUserRole()
        if (userRole == null || allowedRoles.none { userRole.includes(it) }) {
            call.respond(HttpStatusCode.Forbidden, "Access denied: insufficient permissions")
        }
    }
}

/**
 * Ensures that the authenticated user has one of [allowedRoles].
 * Otherwise responds with 401 (no principal) or 403 (wrong role) and the route handler is not executed.
 */
fun Route.authorize(vararg allowedRoles: UserRole, build: Route.() -> Unit): Route {
    val authorizedRoute = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
            RouteSelectorEvaluation.Constant

        override fun toString(): String = "(roles: ${allowedRoles.joinToString()})"
    })

    val authenticatedRoute = authorizedRoute.authenticate("auth-jwt") {
        install(RoleAuthorizationPlugin) {
            this.allowedRoles = allowedRoles.toSet()
        }
        build()
    }
    return authenticatedRoute
}
