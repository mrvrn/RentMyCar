package com.rentmycar.domain.rental.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateRentalDto(
    val carId: Long,
    val startAt: String,
    val endAt: String
)