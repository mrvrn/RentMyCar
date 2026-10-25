package com.rentmycar.domain.car.cost

import java.math.BigDecimal

/**
 * Cost model for an internal-combustion engine vehicle.
 */
final class IceCostModel(consumptionPer100Km: BigDecimal) : CarCostModel(consumptionPer100Km) {
    override val unitPrice: BigDecimal = CostAssumptions.ICE_UNIT_PRICE
    override val annualFixedCost: BigDecimal = CostAssumptions.ICE_ANNUAL_FIXED_COST
}
