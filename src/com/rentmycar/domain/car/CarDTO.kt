package com.rentmycar.domain.car

import kotlinx.serialization.Serializable

@Serializable
data class AddCarRequest(
    val brand: String,
    val type: String,
    val licensePlate: String,
    val pricePerDay: Double,
    val pickupLocationId: Long,
    val dropOffLocationId: Long,
    val pictures: List<String>? = null,
    val isAvailable: Boolean = false
)

@Serializable
data class CarResponse(
    val id: Long,
    val ownerId: Long,
    val brand: String,
    val type: String,
    val licensePlate: String,
    val pricePerDay: Double,
    val pickupLocationId: Long,
    val dropOffLocationId: Long,
    val pictures: List<String>?,
    val isAvailable: Boolean
)
