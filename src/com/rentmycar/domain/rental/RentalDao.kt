package com.rentmycar.domain.rental

import com.rentmycar.domain.car.Car
import com.rentmycar.domain.user.User
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

class Rental(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Rental>(Rentals)

    var car by Car referencedOn Rentals.carId
    var renter by User referencedOn Rentals.renterId
    var startAt by Rentals.startAt
    var endAt by Rentals.endAt
    var status by Rentals.status
    var totalPrice by Rentals.totalPrice
    var createdAt by Rentals.createdAt
    var cancelledAt by Rentals.cancelledAt
}