package com.rentmycar.domain.user

import com.rentmycar.domain.user.dto.UserDto
import com.rentmycar.domain.user.dto.CreateUserDto

interface IUserRepository {
    fun toDto(entity: User): UserDto
    suspend fun findAll(): List<UserDto>
    suspend fun findById(id: Long): UserDto?
    suspend fun delete(id: Long): Boolean
    suspend fun create(user: CreateUserDto)
    suspend fun findByEmail(email: String): User?
}
