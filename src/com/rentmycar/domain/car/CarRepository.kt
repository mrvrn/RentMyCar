package com.rentmycar.domain.car

import com.rentmycar.database.withTransaction
import com.rentmycar.domain.base.BaseRepository
import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto
import com.rentmycar.domain.location.Location
import com.rentmycar.domain.user.User
import java.util.Locale
import org.jetbrains.exposed.v1.core.eq
import java.sql.SQLException
import java.util.Locale

class CarRepository : ICarRepository, BaseRepository<Car, CarDto>(Car) {
    override fun toDto(entity: Car): CarDto = entity.toDto()

<<<<<<< HEAD
    override suspend fun create(car: CreateCarDto) {
        withTransaction {
            val pickupLocation = Location.findById(car.pickupId)
                ?: throw InvalidCarLocationException()
            val dropOffLocation = Location.findById(car.dropOffId)
                ?: throw InvalidCarLocationException()

            Car.new {
                owner = User[car.ownerId]
                brand = car.brand
                type = car.type
                licensePlate = car.licensePlate.trim().uppercase(Locale.ROOT)
                pricePerHour = car.pricePerHour
                pickup = pickupLocation
                dropOff = dropOffLocation
                pictures = car.pictures
                isAvailable = car.isAvailable
            }
=======
    override suspend fun create(car: CreateCarDto, ownerId: Long): CarDto {
        try {
            return withTransaction {
                val duplicate = Car.find { Cars.licensePlate eq car.licensePlate }.firstOrNull()
                if (duplicate != null) {
                    throw DuplicateLicensePlateException(IllegalStateException("Dit kenteken is al in gebruik"))
                }

                val ownerUser = User.findById(ownerId)
                    ?: throw IllegalArgumentException("Eigenaar niet gevonden")
                val pickupLocation = Location.findById(car.pickupId)
                    ?: throw IllegalArgumentException("Ophaallocatie met ID ${car.pickupId} bestaat niet")
                val dropOffLocation = Location.findById(car.dropOffId)
                    ?: throw IllegalArgumentException("Inleverlocatie met ID ${car.dropOffId} bestaat niet")

                val newCar = Car.new {
                    owner = ownerUser
                    brand = car.brand
                    type = car.type
                    licensePlate = car.licensePlate.trim().uppercase(Locale.ROOT)
                    pricePerHour = car.pricePerHour
                    pickup = pickupLocation
                    dropOff = dropOffLocation
                    pictures = car.pictures
                    isAvailable = car.isAvailable
                }

                toDto(newCar)
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
        try {
            return withTransaction {
                val existing = Car.findById(id) ?: return@withTransaction null

                car.licensePlate?.let { newPlate ->
                    val normalizedPlate = newPlate.trim().uppercase(Locale.ROOT)
                    if (normalizedPlate != existing.licensePlate) {
                        val dup = Car.find { Cars.licensePlate eq normalizedPlate }.firstOrNull()
                        if (dup != null) {
                            throw DuplicateLicensePlateException(IllegalStateException("Dit kenteken is al in gebruik"))
                        }
                        existing.licensePlate = normalizedPlate
                    }
                }

                car.brand?.let { existing.brand = it }
                car.type?.let { existing.type = it }
                car.pricePerHour?.let { existing.pricePerHour = it }
                car.pickupId?.let { locId ->
                    val loc = Location.findById(locId)
                        ?: throw IllegalArgumentException("Ophaallocatie met ID $locId bestaat niet")
                    existing.pickup = loc
                }
                car.dropOffId?.let { locId ->
                    val loc = Location.findById(locId)
                        ?: throw IllegalArgumentException("Inleverlocatie met ID $locId bestaat niet")
                    existing.dropOff = loc
                }
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

class InvalidCarLocationException : RuntimeException()
