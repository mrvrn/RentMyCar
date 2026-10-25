package com.rentmycar.domain.car

import com.rentmycar.domain.location.Location
import com.rentmycar.domain.rental.Rental
import com.rentmycar.domain.rental.Rentals
import com.rentmycar.domain.user.User
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * Exposed DAO (Data Access Object) representing a row in the [Cars] table.
 *
 * Each property maps to a database column. Changes to these properties are
 * tracked by Exposed and saved when the transaction commits.
 *
 * Create, load, and update instances inside a database transaction.
 */
class Car(id: EntityID<Long>) : LongEntity(id) {
    /** Provides Exposed's DAO operations, such as creating and finding Cars. */
    companion object : LongEntityClass<Car>(Cars)

    var owner by User referencedOn Cars.ownerId
    var brand by Cars.brand
    var type by Cars.type
    var licensePlate by Cars.licensePlate
    var pricePerHour by Cars.pricePerHour
    var pickup by Location referencedOn Cars.pickupId
    var dropOff by Location referencedOn Cars.dropOffId
    var pictures by Cars.pictures
    var isAvailable by Cars.isAvailable
    var category by Cars.category
    var fuelType by Cars.fuelType
    var consumptionPer100Km by Cars.consumptionPer100Km

    val rentals by Rental referrersOn Rentals.carId // make sure to use val and referrersOn
}
