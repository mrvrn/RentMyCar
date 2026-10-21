package com.rentmycar.domain.car.dto

<<<<<<< HEAD
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal
=======
import kotlinx.serialization.Serializable
>>>>>>> ceb7995 (Refactor car CRUD to Exposed DAO BaseRepository architecture matching master)

@Serializable
data class CarDto(
    val id: Long,
    val ownerId: Long,
    val brand: String,
    val type: String,
    val licensePlate: String,
<<<<<<< HEAD
    @Contextual val pricePerHour: BigDecimal,
    val pickupId: Long,
    val dropOffId: Long,
    val pictures: List<String>?,
=======
    val pricePerDay: Double,
    val pickupLocationId: Long,
    val dropOffLocationId: Long,
    val pictures: List<String>? = null,
>>>>>>> ceb7995 (Refactor car CRUD to Exposed DAO BaseRepository architecture matching master)
    val isAvailable: Boolean
)
