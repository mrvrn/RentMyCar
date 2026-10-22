package com.rentmycar.domain.car

import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto

interface ICarRepository {
    fun toDto(entity: Car): CarDto
    suspend fun findAll(): List<CarDto>
    suspend fun findById(id: Long): CarDto?
    suspend fun delete(id: Long): Boolean
    suspend fun create(car: CreateCarDto)
}
