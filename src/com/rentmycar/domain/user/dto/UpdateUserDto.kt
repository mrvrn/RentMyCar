package com.rentmycar.domain.user.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateUserDto(
    val id: Long,
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val password: String? = null
)
