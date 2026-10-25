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
        require(car.pricePerHour.signum() >= 0) { "Hourly rate must not be negative" }
        withTransaction {
            Car.new {
                owner = User[car.ownerId]
                brand = car.brand
                type = car.type
                licensePlate = car.licensePlate
                pricePerHour = car.pricePerHour
                pickup = Location[car.pickupId]
                dropOff = Location[car.dropOffId]
                pictures = car.pictures
                isAvailable = car.isAvailable
            }
        }
    }
}
