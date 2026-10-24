package com.rentmycar.domain.rental

import com.rentmycar.domain.rental.dto.CreateRentalDto
import com.rentmycar.domain.rental.dto.RentalDto
import com.rentmycar.domain.rental.dto.RentalOverviewDto

interface IRentalRepository {
    fun toDto(entity: Rental): RentalDto
    suspend fun findAll(): List<RentalDto>
    suspend fun findById(id: Long): RentalDto?
    suspend fun findAllByUserId(userId: Long): List<RentalOverviewDto>
    suspend fun create(rental: CreateRentalDto, renterId: Long, carId: Long): RentalDto
    suspend fun changeStatus(rentalId: Long, status: RentalStatus, loggedInUserId: Long): RentalDto
}
