package com.rentmycar.domain.car

import com.rentmycar.domain.car.dto.CarDto

fun Car.toDto(): CarDto = CarDto(
    id = id.value,
    ownerId = owner.id.value,
    brand = brand,
    type = type,
    licensePlate = licensePlate,
<<<<<<< HEAD
    pricePerHour = pricePerHour,
    pickupId = pickup.id.value,
    dropOffId = dropOff.id.value,
=======
    pricePerDay = pricePerDay.toDouble(),
    pickupLocationId = pickup.id.value,
    dropOffLocationId = dropOff.id.value,
>>>>>>> ceb7995 (Refactor car CRUD to Exposed DAO BaseRepository architecture matching master)
    pictures = pictures,
    isAvailable = isAvailable
)
