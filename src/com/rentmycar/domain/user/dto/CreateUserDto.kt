package com.rentmycar.domain.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateUserDto(
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    val password: String
)