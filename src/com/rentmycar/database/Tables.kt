package com.rentmycar.database

import com.rentmycar.domain.car.Cars
import com.rentmycar.domain.location.Locations
import com.rentmycar.domain.rental.Rentals
import com.rentmycar.domain.user.Users
import org.jetbrains.exposed.v1.core.Table

val applicationTables: Array<Table> = arrayOf(Users, Locations, Cars, Rentals)



