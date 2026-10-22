package com.rentmycar

import com.rentmycar.domain.rental.RentalRepository
import com.rentmycar.domain.car.CarRepository
import com.rentmycar.domain.user.UserRepository
import com.rentmycar.server.routing.carRoutes
import com.rentmycar.server.routing.rentalRoutes
import com.rentmycar.server.routing.userRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        carRoutes(CarRepository())
        userRoutes(UserRepository())
        rentalRoutes(RentalRepository())
    }
}
