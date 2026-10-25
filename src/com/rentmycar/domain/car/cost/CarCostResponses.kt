package com.rentmycar.domain.car.cost

import com.rentmycar.domain.car.CarCategory
import com.rentmycar.domain.car.dto.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

/**
 * Response containing a car's calculated consumption cost.
 */
@Serializable
data class ConsumptionCostResponse(
    val carId: Long,
    val category: CarCategory,
    @Serializable(with = BigDecimalSerializer::class) val consumptionCostPerKm: BigDecimal
)

/**
 * Response containing a car's calculated annual total cost of ownership.
 */
@Serializable
data class TcoResponse(
    val carId: Long,
    val category: CarCategory,
    val kmPerYear: Int,
    @Serializable(with = BigDecimalSerializer::class) val tco: BigDecimal
)
