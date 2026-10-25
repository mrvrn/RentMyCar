package com.rentmycar.domain.car.cost

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Calculates running consumption cost and total cost of ownership for a propulsion type.
 *
 * Consumption cost is `(consumptionPer100Km / 100) * unitPrice`, rounded to two decimals.
 * TCO is `consumptionCostPerKm * kmPerYear + annualFixedCost`.
 * Prices are placeholders for the project and should be replaced by current assumptions.
 */
abstract class CarCostModel(protected val consumptionPer100Km: BigDecimal) {
    /** Lazily creates the shared divisor used by the consumption formula. */
    private val oneHundred by lazy { BigDecimal("100") }
    /** Placeholder price per litre, kWh, or kilogram of hydrogen. */
    abstract val unitPrice: BigDecimal

    /** Placeholder insurance and maintenance cost per year. */
    abstract val annualFixedCost: BigDecimal

    /**
     * Calculates the consumption cost for one kilometre, rounded half-up to two decimals.
     */
    fun calculateConsumptionCostPerKm(): BigDecimal =
        (consumptionPer100Km.divide(oneHundred) * unitPrice)
            .setScale(2, RoundingMode.HALF_UP)

    /**
     * Calculates annual total cost of ownership for [kmPerYear] kilometres.
     */
    fun calculateTco(kmPerYear: Int): BigDecimal {
        require(kmPerYear >= 0) { "Kilometres per year must not be negative" }
        return (calculateConsumptionCostPerKm() * kmPerYear.toBigDecimal() + annualFixedCost)
            .setScale(2, RoundingMode.HALF_UP)
    }
}
