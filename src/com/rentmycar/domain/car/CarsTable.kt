package com.rentmycar.domain.car

import com.rentmycar.domain.location.Locations
import com.rentmycar.domain.user.Users
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.json.json


object Cars : LongIdTable("cars") {
    val ownerId = reference("ownerId", Users.id, onDelete = ReferenceOption.CASCADE)
    val brand  = varchar("brand", 255)
    val type = varchar("type", 255)
    val licensePlate = varchar("licensePlate", 255).uniqueIndex("idx_unique_license_plate")
    val pricePerHour = decimal("pricePerHour", 10, 2)
    val pickupId = reference("pickupId", Locations.id)
    val dropOffId = reference("dropOffId", Locations.id)
    val pictures = json<List<String>>("pictures", Json.Default).nullable()
    val isAvailable = bool("isAvailable").default(false)
}
