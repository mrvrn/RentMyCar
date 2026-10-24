package com.rentmycar.server.routing

import com.rentmycar.domain.user.IUserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

internal suspend fun ApplicationCall.userIdOrRespond(userRepository: IUserRepository): Long? {
    val userId = principal<JWTPrincipal>()
        ?.payload?.getClaim("userId")?.asLong()

    if (userId == null || userRepository.findById(userId) == null) {
        respond(HttpStatusCode.Unauthorized)
        return null
    }

    return userId
}