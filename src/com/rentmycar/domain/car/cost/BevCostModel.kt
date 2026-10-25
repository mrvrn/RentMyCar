package com.rentmycar.domain.car.cost

import java.math.BigDecimal

/**
 * Cost model for a battery-electric vehicle.
 */
final class BevCostModel(consumptionPer100Km: BigDecimal) : CarCostModel(consumptionPer100Km) {
    override val unitPrice: BigDecimal = CostAssumptions.BEV_UNIT_PRICE
    override val annualFixedCost: BigDecimal = CostAssumptions.BEV_ANNUAL_FIXED_COST
}
