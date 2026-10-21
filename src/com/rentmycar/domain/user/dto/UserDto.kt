package com.rentmycar.domain.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    val bonusPoints: Long?
)
