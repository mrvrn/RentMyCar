package com.rentmycar.domain.rental.dto

import com.rentmycar.domain.car.Car
import com.rentmycar.domain.rental.RentalStatus
import kotlinx.serialization.Serializable

@Serializable
data class RentalDto (
    val id: Long,
    val carId: Long,
    val renterId: Long,
    val startAt: String,
    val endAt: String,
    val status: RentalStatus,
    val totalPrice: String,
    val createdAt: String,
    val cancelledAt: String?
)
