package com.rentmycar.domain.car.cost

import java.math.BigDecimal

/**
 * Central placeholder assumptions used by all car cost models.
 *
 * The existing object declaration and companion object usage remain part of the car domain design.
 */
object CostAssumptions {
    // These values are placeholders for the school project, not current market prices.
    val ICE_UNIT_PRICE = BigDecimal("2.00")
    val ICE_ANNUAL_FIXED_COST = BigDecimal("1200.00")
    val BEV_UNIT_PRICE = BigDecimal("0.35")
    val BEV_ANNUAL_FIXED_COST = BigDecimal("900.00")
    val FCEV_UNIT_PRICE = BigDecimal("8.00")
    val FCEV_ANNUAL_FIXED_COST = BigDecimal("1100.00")
}
