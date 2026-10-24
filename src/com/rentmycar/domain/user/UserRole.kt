package com.rentmycar.domain.user

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    OWNER,
    RENTER;

    companion object {
        private val legacyValues = mapOf(
            "AANBIEDER" to OWNER,
            "HUURDER" to RENTER,
            "aanbieder" to OWNER,
            "huurder" to RENTER,
        )

        fun fromString(value: String?): UserRole? {
            if (value == null) return null
            return legacyValues[value] ?: entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
