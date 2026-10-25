package com.rentmycar.domain.car

import com.rentmycar.domain.car.dto.CarDto

fun Car.toDto(): CarDto = CarDto(
    id = id.value,
    ownerId = owner.id.value,
    brand = brand,
    type = type,
    licensePlate = licensePlate,
    pricePerHour = pricePerHour,
    pickupId = pickup.id.value,
    dropOffId = dropOff.id.value,
    pictures = pictures,
    isAvailable = isAvailable,
    category = CarCategory.fromDatabase(category),
    fuelType = fuelType,
    consumptionPer100Km = consumptionPer100Km
)
