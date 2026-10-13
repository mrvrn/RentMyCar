package com.rentmycar.domain.car

import org.jetbrains.exposed.v1.sql.transactions.transaction
import java.math.BigDecimal

object CarService {
    fun addCar(request: AddCarRequest, ownerId: Long): CarResponse {
        return transaction {
            val pickupLocationExists = com.rentmycar.domain.location.Locations
                .slice(com.rentmycar.domain.location.Locations.id)
                .select { com.rentmycar.domain.location.Locations.id eq request.pickupLocationId }
                .count() > 0
            
            if (!pickupLocationExists) {
                throw IllegalArgumentException("Ophaallocatie met ID ${request.pickupLocationId} bestaat niet")
            }
            
            val dropOffLocationExists = com.rentmycar.domain.location.Locations
                .slice(com.rentmycar.domain.location.Locations.id)
                .select { com.rentmycar.domain.location.Locations.id eq request.dropOffLocationId }
                .count() > 0
            
            if (!dropOffLocationExists) {
                throw IllegalArgumentException("Inleverlocatie met ID ${request.dropOffLocationId} bestaat niet")
            }
            
            val licensePlateExists = Cars
                .slice(Cars.licensePlate)
                .select { Cars.licensePlate eq request.licensePlate }
                .count() > 0
            
            if (licensePlateExists) {
                throw IllegalArgumentException("Er bestaat al een auto met kenteken ${request.licensePlate}")
            }
            
            val priceAsBigDecimal = BigDecimal.valueOf(request.pricePerDay)
            
            val carId = Cars.insert {
                it[ownerId] = ownerId
                it[brand] = request.brand
                it[type] = request.type
                it[licensePlate] = request.licensePlate
                it[pricePerDay] = priceAsBigDecimal
                it[pickupId] = request.pickupLocationId
                it[dropOffId] = request.dropOffLocationId
                it[pictures] = request.pictures
                it[isAvailable] = request.isAvailable
            } get Cars.id
            
            val car = Cars
                .select { Cars.id eq carId!! }
                .single()
            
            CarResponse(
                id = car[Cars.id],
                ownerId = car[Cars.ownerId],
                brand = car[Cars.brand],
                type = car[Cars.type],
                licensePlate = car[Cars.licensePlate],
                pricePerDay = car[Cars.pricePerDay].toDouble(),
                pickupLocationId = car[Cars.pickupId],
                dropOffLocationId = car[Cars.dropOffId],
                pictures = car[Cars.pictures],
                isAvailable = car[Cars.isAvailable]
            )
        }
    }
}
