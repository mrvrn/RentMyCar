package com.rentmycar.domain.car.dto

<<<<<<< HEAD
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
=======
import kotlinx.serialization.Serializable

@Serializable
data class CreateCarDto(
    val brand: String,
    val type: String,
    val licensePlate: String,
    val pricePerDay: Double,
    val pickupLocationId: Long,
    val dropOffLocationId: Long,
>>>>>>> ceb7995 (Refactor car CRUD to Exposed DAO BaseRepository architecture matching master)
    val pictures: List<String>? = null,
    val isAvailable: Boolean = false
)
