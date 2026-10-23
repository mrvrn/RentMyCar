package com.rentmycar.domain.car.dto

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class CarDto(
    val id: Long,
    val ownerId: Long,
    val brand: String,
    val type: String,
    val licensePlate: String,
    @Serializable(with = BigDecimalSerializer::class) val pricePerHour: BigDecimal,
    val pickupId: Long,
    val dropOffId: Long,
    val pictures: List<String>?,
    val isAvailable: Boolean
)
