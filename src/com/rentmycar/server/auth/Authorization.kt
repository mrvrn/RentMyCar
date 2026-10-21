package com.rentmycar.server.auth

import com.rentmycar.domain.user.UserRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.*

fun ApplicationCall.authenticatedUserId(): Long? =
    principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asLong()

fun ApplicationCall.authenticatedUserRole(): UserRole? =
    UserRole.fromString(principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString())

suspend fun ApplicationCall.requireRole(vararg allowedRoles: UserRole): Boolean {
    val role = authenticatedUserRole()
    if (role == null || role !in allowedRoles) {
        respond(HttpStatusCode.Forbidden, "Access denied: insufficient permissions")
        return false
    }
    return true
}

class RoleAuthorizationConfig {
    var allowedRoles: Set<UserRole> = emptySet()
}

val RoleAuthorizationPlugin = createRouteScopedPlugin(
    name = "RoleAuthorization",
    createConfiguration = ::RoleAuthorizationConfig
) {
    val allowedRoles = pluginConfig.allowedRoles
    on(AuthenticationChecked) { call ->
        val principal = call.principal<JWTPrincipal>() ?: return@on
        val roleClaim = principal.payload.getClaim("role")?.asString()
        val userRole = UserRole.fromString(roleClaim)

        if (userRole == null || userRole !in allowedRoles) {
            call.respond(HttpStatusCode.Forbidden, "Access denied: insufficient permissions")
        }
    }
}

/**
 * Ensures that the authenticated user has one of [allowedRoles].
 * If not, the request responds with 403 Forbidden.
 */
fun Route.authorize(vararg allowedRoles: UserRole, build: Route.() -> Unit): Route {
    val authorizedRoute = createChild(object : RouteSelector() {
        override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
            RouteSelectorEvaluation.Constant

        override fun toString(): String = "(roles: ${allowedRoles.joinToString()})"
    })

    authorizedRoute.install(RoleAuthorizationPlugin) {
        this.allowedRoles = allowedRoles.toSet()
    }
    authorizedRoute.build()
    return authorizedRoute
}
