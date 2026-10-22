package com.rentmycar.domain.car.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class CreateCarDto(
    val ownerId: Long,
    val brand: String,
    val type: String,
    val licensePlate: String,
    @Contextual val pricePerHour: BigDecimal,
    val pickupId: Long,
    val dropOffId: Long,
    val pictures: List<String>? = null,
    val isAvailable: Boolean = false
)
