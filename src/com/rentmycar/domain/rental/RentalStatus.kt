package com.rentmycar.domain.rental

import kotlinx.serialization.Serializable

@Serializable
enum class RentalStatus {
    PENDING,
    CONFIRMED,
    ACTIVE,
    COMPLETED,
    CANCELLED
}
