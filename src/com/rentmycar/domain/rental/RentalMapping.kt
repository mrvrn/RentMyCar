package com.rentmycar.domain.rental

import com.rentmycar.domain.car.dto.RentalCarDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.rental.dto.RentalOverviewDto

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

fun Rental.toOverviewDto() = RentalOverviewDto(
    id = id.value,
    startAt = startAt.toString(),
    endAt = endAt.toString(),
    status = status,
    totalPrice = totalPrice.toPlainString(),
    car = RentalCarDto(
        id = car.id.value,
        brand = car.brand,
        type = car.type,
        pictures = car.pictures?.firstOrNull(),
    ),
)