package com.rentmycar.domain.user

import com.rentmycar.domain.car.Car
import com.rentmycar.domain.car.Cars
import com.rentmycar.domain.location.Location
import com.rentmycar.domain.location.Locations
import com.rentmycar.domain.rental.Rental
import com.rentmycar.domain.rental.Rentals
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * Exposed DAO (Data Access Object) representing a row in the [Users] table.
 *
 * Each property maps to a database column. Changes to these properties are
 * tracked by Exposed and saved when the transaction commits.
 *
 * Create, load, and update instances inside a database transaction.
 */
class User(id: EntityID<Long>) : LongEntity(id) {
    /** Provides Exposed's DAO operations, such as creating and finding users. */
    companion object : LongEntityClass<User>(Users)

    var name by Users.name
    var email by Users.email
    var phone by Users.phone
    var address by Users.address
    var bonusPoints by Users.bonusPoints
    var password by Users.password
    var role by Users.role

    val cars by Car referrersOn Cars.ownerId // make sure to use val and referrersOn
    val locations by Location referrersOn Locations.userId
    val rentals by Rental referrersOn Rentals.renterId
}
