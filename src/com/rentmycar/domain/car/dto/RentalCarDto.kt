package com.rentmycar.domain.car.dto

import kotlinx.serialization.Serializable

@Serializable
data class RentalCarDto(
    val id: Long,
    val brand: String,
    val type: String,
    val pictures: String?
)
