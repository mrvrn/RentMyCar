package com.rentmycar.domain.rental.dto

import com.rentmycar.domain.car.dto.RentalCarDto
import com.rentmycar.domain.rental.RentalStatus
import kotlinx.serialization.Serializable

@Serializable
data class RentalDetailsDto(
    val id: Long,
    val startAt: String,
    val endAt: String,
    val status: RentalStatus,
    val totalPrice: String,
    val car: RentalCarDto,
    val renterId: Long,
    val createdAt: String,
    val cancelledAt: String?,
)
