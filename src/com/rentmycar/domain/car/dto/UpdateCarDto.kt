package com.rentmycar.domain.car.dto

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class UpdateCarDto(
    val brand: String? = null,
    val type: String? = null,
    val licensePlate: String? = null,
    @Serializable(with = BigDecimalSerializer::class) val pricePerHour: BigDecimal? = null,
    val pickupId: Long? = null,
    val dropOffId: Long? = null,
    val pictures: List<String>? = null,
    val isAvailable: Boolean? = null
)
