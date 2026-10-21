package com.rentmycar.domain.user

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    AANBIEDER,
    HUURDER;

    companion object {
        fun fromString(value: String?): UserRole? = when (value?.trim()?.uppercase()) {
            "AANBIEDER", "CAR_OWNER", "OWNER" -> AANBIEDER
            "HUURDER", "RENTER", "CUSTOMER" -> HUURDER
            else -> null
        }
    }
}
