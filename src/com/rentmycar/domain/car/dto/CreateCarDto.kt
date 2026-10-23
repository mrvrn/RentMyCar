package com.rentmycar.domain.car.dto

import kotlinx.serialization.Serializable
import java.math.BigDecimal

/** The owner is not part of the request: it is taken from the authenticated user's JWT. */
@Serializable
data class CreateCarDto(
    val brand: String,
    val type: String,
    val licensePlate: String,
    @Serializable(with = BigDecimalSerializer::class) val pricePerHour: BigDecimal,
    val pickupId: Long,
    val dropOffId: Long,
    val pictures: List<String>? = null,
    val isAvailable: Boolean = false
)
