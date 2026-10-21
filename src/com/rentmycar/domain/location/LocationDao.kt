package com.rentmycar.domain.location

import com.rentmycar.domain.user.User
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass

/**
 * Exposed DAO (Data Access Object) representing a row in the [Locations] table.
 *
 * Each property maps to a database column. Changes to these properties are
 * tracked by Exposed and saved when the transaction commits.
 *
 * Create, load, and update instances inside a database transaction.
 */
class Location(id: EntityID<Long>) : LongEntity(id) {
    /** Provides Exposed's DAO operations, such as creating and finding Locations. */
    companion object : LongEntityClass<Location>(Locations)

    var streetName by Locations.streetName
    var houseNumber by Locations.houseNumber
    var houseNumberAddition by Locations.houseNumberAddition
    var postalCode by Locations.postalCode
    var city by Locations.city
    var country by Locations.country
    var user by User referencedOn Locations.userId
}
