package com.rentmycar.domain.car.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateCarDto(
    val brand: String? = null,
    val type: String? = null,
    val licensePlate: String? = null,
    val pricePerDay: Double? = null,
    val pickupLocationId: Long? = null,
    val dropOffLocationId: Long? = null,
    val pictures: List<String>? = null,
    val isAvailable: Boolean? = null
)
