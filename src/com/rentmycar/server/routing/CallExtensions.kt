package com.rentmycar.server.routing

import com.rentmycar.domain.car.ICarRepository
import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.rental.IRentalRepository
import com.rentmycar.domain.rental.RentalAccessDeniedException
import com.rentmycar.domain.rental.dto.RentalDetailsDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.user.IUserRepository
import com.rentmycar.domain.user.dto.UserDto
import com.rentmycar.server.auth.authenticatedUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal suspend fun ApplicationCall.userOrRespond(userRepository: IUserRepository): UserDto? {
    val user = authenticatedUserId()?.let { userRepository.findById(it) }
    if (user == null) {
        respond(HttpStatusCode.Unauthorized)
    }
    return user
}

internal suspend fun ApplicationCall.carOrRespond(carRepository: ICarRepository): CarDto? {
    val id = resourceIdOrRespond("car") ?: return null
    val car = carRepository.findById(id)
    if (car == null) {
        respond(HttpStatusCode.NotFound, "Car not found")
    }
    return car
}

internal suspend fun ApplicationCall.rentalOrRespond(rentalRepository: IRentalRepository): RentalDto? {
    val id = resourceIdOrRespond("rental") ?: return null
    val rental = rentalRepository.findById(id)
    if (rental == null) {
        respond(HttpStatusCode.NotFound, "Rental not found")
    }
    return rental
}

// Fetch the authorized details directly, without an additional raw rental lookup.
internal suspend fun ApplicationCall.rentalOrRespond(
    rentalRepository: IRentalRepository,
    loggedInUserId: Long,
): RentalDetailsDto? {
    val id = resourceIdOrRespond("rental") ?: return null
    val rental = try {
        rentalRepository.findDetailsById(id, loggedInUserId)
    } catch (exception: RentalAccessDeniedException) {
        respond(HttpStatusCode.Forbidden, exception.message.orEmpty())
        return null
    }
    if (rental == null) {
        respond(HttpStatusCode.NotFound, "Rental not found")
    }
    return rental
}

private suspend fun ApplicationCall.resourceIdOrRespond(resource: String): Long? {
    val id = parameters["id"]?.toLongOrNull()?.takeIf { it > 0 }
    if (id == null) {
        respond(HttpStatusCode.BadRequest, "Invalid $resource ID")
    }
    return id
}
