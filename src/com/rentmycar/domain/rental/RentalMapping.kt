package com.rentmycar.domain.rental

import com.rentmycar.domain.rental.Rentals.carId
import com.rentmycar.domain.rental.dto.RentalDto

fun Rental.toDto(): RentalDto = RentalDto(
    id = id.value,
    carId = car.id.value,
    renterId = renter.id.value,
    startAt = startAt.toString(),
    endAt = endAt.toString(),
    status = status,
    totalPrice = totalPrice.toString(),
    createdAt = createdAt.toString(),
    cancelledAt = cancelledAt?.toString()
)