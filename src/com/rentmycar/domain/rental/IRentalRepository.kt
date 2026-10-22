package com.rentmycar.domain.rental

import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.dto.RentalDto

interface IRentalRepository {
    fun toDto(entity: Rental): RentalDto
    suspend fun findAll(): List<RentalDto>
    suspend fun findById(id: Long): RentalDto?
    suspend fun create(rental: CreateRentalDto, renterId: Long, carId: Long)
}