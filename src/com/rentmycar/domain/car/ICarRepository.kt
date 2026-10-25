package com.rentmycar.domain.car

import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto
import com.rentmycar.domain.car.dto.UpdateCarDto

interface ICarRepository {
    fun toDto(entity: Car): CarDto
    suspend fun findAll(): List<CarDto>
    suspend fun findById(id: Long): CarDto?
    suspend fun delete(id: Long): Boolean
    suspend fun create(car: CreateCarDto, ownerId: Long): CarDto
    suspend fun update(id: Long, car: UpdateCarDto): CarDto?
}
