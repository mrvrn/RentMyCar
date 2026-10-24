package com.rentmycar.domain.car

import com.rentmycar.database.withTransaction
import com.rentmycar.domain.base.BaseRepository
import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.location.Location
import com.rentmycar.domain.user.User

class CarRepository : ICarRepository, BaseRepository<Car, CarDto>(Car) {
    override fun toDto(entity: Car): CarDto = entity.toDto()

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
                licensePlate = car.licensePlate
                pricePerHour = car.pricePerHour
                pickup = pickupLocation
                dropOff = dropOffLocation
                pictures = car.pictures
                isAvailable = car.isAvailable
            }
        }
    }
}

class InvalidCarLocationException : RuntimeException()
