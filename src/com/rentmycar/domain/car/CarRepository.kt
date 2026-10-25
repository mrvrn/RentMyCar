package com.rentmycar.domain.car

import com.rentmycar.database.withTransaction
import com.rentmycar.domain.base.BaseRepository
import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import com.rentmycar.domain.location.Location
import com.rentmycar.domain.user.User
import org.jetbrains.exposed.v1.core.eq
import java.sql.SQLException
import java.util.Locale

class CarRepository : ICarRepository, BaseRepository<Car, CarDto>(Car) {
    override fun toDto(entity: Car): CarDto = entity.toDto()

    override suspend fun delete(id: Long): Boolean {
        try {
            return super.delete(id)
        } catch (exception: SQLException) {
            // The database constraint also protects history when a booking races with deletion.
            if (generateSequence<Throwable>(exception) { it.cause }
                    .filterIsInstance<SQLException>().any { it.errorCode == 1451 }) {
                throw CarHasRentalsException(exception)
            }
            throw exception
        }
    }

    override suspend fun create(car: CreateCarDto, ownerId: Long): CarDto {
        val validated = CarValidation.normalizeAndValidate(car)
        require(ownerId > 0) { "Owner ID must be positive" }
        return createValidated(validated, ownerId)
    }

    private suspend fun createValidated(car: CreateCarDto, ownerId: Long): CarDto {
        try {
            return withTransaction {
                val normalizedPlate = car.licensePlate.trim().uppercase(Locale.ROOT)
                if (Car.find { Cars.licensePlate eq normalizedPlate }.firstOrNull() != null) {
                    throw DuplicateLicensePlateException(IllegalStateException("License plate is already in use"))
                }

                val ownerUser = User.findById(ownerId)
                    ?: throw IllegalArgumentException("Owner not found")
                val pickupLocation = Location.findById(car.pickupId)
                    ?: throw IllegalArgumentException("Pickup location with ID ${car.pickupId} does not exist")
                val dropOffLocation = Location.findById(car.dropOffId)
                    ?: throw IllegalArgumentException("Drop-off location with ID ${car.dropOffId} does not exist")

                toDto(Car.new {
                    owner = ownerUser
                    brand = car.brand
                    type = car.type
                    licensePlate = normalizedPlate
                    pricePerHour = car.pricePerHour
                    pickup = pickupLocation
                    dropOff = dropOffLocation
                    pictures = car.pictures
                    isAvailable = car.isAvailable
                })
            }
        } catch (exception: SQLException) {
            if (generateSequence<Throwable>(exception) { it.cause }
                    .filterIsInstance<SQLException>().any { it.errorCode == 1062 }) {
                throw DuplicateLicensePlateException(exception)
            }
            throw exception
        }
    }

    override suspend fun update(id: Long, car: UpdateCarDto): CarDto? {
        val validated = CarValidation.normalizeAndValidate(car)
        require(id > 0) { "Car ID must be positive" }
        return updateValidated(id, validated)
    }

    private suspend fun updateValidated(id: Long, car: UpdateCarDto): CarDto? {
        try {
            return withTransaction {
                val existing = Car.findById(id) ?: return@withTransaction null

                car.licensePlate?.let { newPlate ->
                    val normalizedPlate = newPlate.trim().uppercase(Locale.ROOT)
                    if (normalizedPlate != existing.licensePlate) {
                        if (Car.find { Cars.licensePlate eq normalizedPlate }.firstOrNull() != null) {
                            throw DuplicateLicensePlateException(IllegalStateException("License plate is already in use"))
                        }
                        existing.licensePlate = normalizedPlate
                    }
                }
                car.brand?.let { existing.brand = it }
                car.type?.let { existing.type = it }
                car.pricePerHour?.let { existing.pricePerHour = it }
                car.pickupId?.let { existing.pickup = Location.findById(it)
                    ?: throw IllegalArgumentException("Pickup location with ID $it does not exist") }
                car.dropOffId?.let { existing.dropOff = Location.findById(it)
                    ?: throw IllegalArgumentException("Drop-off location with ID $it does not exist") }
                car.pictures?.let { existing.pictures = it }
                car.isAvailable?.let { existing.isAvailable = it }
                toDto(existing)
            }
        } catch (exception: SQLException) {
            if (generateSequence<Throwable>(exception) { it.cause }
                    .filterIsInstance<SQLException>().any { it.errorCode == 1062 }) {
                throw DuplicateLicensePlateException(exception)
            }
            throw exception
        }
    }
}
