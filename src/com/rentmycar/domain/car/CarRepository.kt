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

private fun validateHourlyRate(price: java.math.BigDecimal) {
    require(price.signum() >= 0) { "Hourly rate must not be negative" }
    require(price <= "99999999.99".toBigDecimal()) { "Hourly rate exceeds the supported maximum" }
    require(price.stripTrailingZeros().scale() <= 2) { "Hourly rate must have at most two decimal places" }
}

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
        validateHourlyRate(car.pricePerHour)
        try {
            return withTransaction {
                val normalizedPlate = car.licensePlate.trim().uppercase(Locale.ROOT)
                if (Car.find { Cars.licensePlate eq normalizedPlate }.firstOrNull() != null) {
                    throw DuplicateLicensePlateException(IllegalStateException("Dit kenteken is al in gebruik"))
                }

                val ownerUser = User.findById(ownerId)
                    ?: throw IllegalArgumentException("Eigenaar niet gevonden")
                val pickupLocation = Location.findById(car.pickupId)
                    ?: throw IllegalArgumentException("Ophaallocatie met ID ${car.pickupId} bestaat niet")
                val dropOffLocation = Location.findById(car.dropOffId)
                    ?: throw IllegalArgumentException("Inleverlocatie met ID ${car.dropOffId} bestaat niet")

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
        car.pricePerHour?.let(::validateHourlyRate)
        try {
            return withTransaction {
                val existing = Car.findById(id) ?: return@withTransaction null

                car.licensePlate?.let { newPlate ->
                    val normalizedPlate = newPlate.trim().uppercase(Locale.ROOT)
                    if (normalizedPlate != existing.licensePlate) {
                        if (Car.find { Cars.licensePlate eq normalizedPlate }.firstOrNull() != null) {
                            throw DuplicateLicensePlateException(IllegalStateException("Dit kenteken is al in gebruik"))
                        }
                        existing.licensePlate = normalizedPlate
                    }
                }
                car.brand?.let { existing.brand = it }
                car.type?.let { existing.type = it }
                car.pricePerHour?.let { existing.pricePerHour = it }
                car.pickupId?.let { existing.pickup = Location.findById(it)
                    ?: throw IllegalArgumentException("Ophaallocatie met ID $it bestaat niet") }
                car.dropOffId?.let { existing.dropOff = Location.findById(it)
                    ?: throw IllegalArgumentException("Inleverlocatie met ID $it bestaat niet") }
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
