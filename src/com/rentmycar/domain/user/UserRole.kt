package com.rentmycar.domain.user

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    AANBIEDER,
    HUURDER;

    companion object {
        fun fromString(value: String?): UserRole? = entries.firstOrNull { it.name == value }
    }
}
