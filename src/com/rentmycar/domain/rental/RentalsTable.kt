package com.rentmycar.domain.rental

import com.rentmycar.domain.car.Cars
import com.rentmycar.domain.user.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.datetime.CurrentDateTime


object Rentals : LongIdTable("rentals") {
    var carId = reference("carId", Cars.id)
    val renterId = reference("renterId", Users.id, onDelete = ReferenceOption.CASCADE)
    val startAt = datetime("startAt")
    val endAt = datetime("endAt")
    val status = enumerationByName<RentalStatus>("status", 50)
    val totalPrice = decimal("totalPrice", 10, 2)
    val createdAt = datetime("createdAt").defaultExpression(CurrentDateTime)
    val cancelledAt = datetime("cancelledAt").nullable()
}
