package com.rentmycar.domain.car

import kotlinx.serialization.Serializable

/**
 * The supported propulsion categories for a car.
 */
@Serializable
enum class CarCategory {
    ICE,
    BEV,
    FCEV;

    companion object {
        /**
         * Converts a persisted category to the domain enum.
         */
        fun fromDatabase(value: String): CarCategory =
            entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unknown car category: $value")
    }
}
