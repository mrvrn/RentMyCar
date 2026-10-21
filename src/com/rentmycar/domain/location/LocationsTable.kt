package com.rentmycar.domain.location

import com.rentmycar.domain.user.Users
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

object Locations : LongIdTable("locations") {
    val streetName = varchar("streetName", 255)
    val houseNumber = varchar("houseNumber", 20)
    val houseNumberAddition = varchar("houseNumberAddition", 20).nullable()
    val postalCode = varchar("postalCode", 20)
    val city = varchar("city", 100)
    val country = varchar("country", 100)
    val userId = reference("userId", Users.id, onDelete = ReferenceOption.CASCADE)
}
