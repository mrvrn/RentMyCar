package com.rentmycar.domain.car

import com.rentmycar.domain.car.dto.CarDto
import com.rentmycar.domain.car.dto.CreateCarDto
<<<<<<< HEAD
=======
import com.rentmycar.domain.car.dto.UpdateCarDto
>>>>>>> ceb7995 (Refactor car CRUD to Exposed DAO BaseRepository architecture matching master)

interface ICarRepository {
    fun toDto(entity: Car): CarDto
    suspend fun findAll(): List<CarDto>
    suspend fun findById(id: Long): CarDto?
    suspend fun delete(id: Long): Boolean
<<<<<<< HEAD
    suspend fun create(car: CreateCarDto)
=======
    suspend fun create(car: CreateCarDto, ownerId: Long): CarDto
    suspend fun update(id: Long, car: UpdateCarDto): CarDto?
>>>>>>> ceb7995 (Refactor car CRUD to Exposed DAO BaseRepository architecture matching master)
}
