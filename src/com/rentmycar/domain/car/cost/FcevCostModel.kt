package com.rentmycar.domain.car.cost

import java.math.BigDecimal

/**
 * Cost model for a hydrogen fuel-cell vehicle.
 */
final class FcevCostModel(consumptionPer100Km: BigDecimal) : CarCostModel(consumptionPer100Km) {
    override val unitPrice: BigDecimal = CostAssumptions.FCEV_UNIT_PRICE
    override val annualFixedCost: BigDecimal = CostAssumptions.FCEV_ANNUAL_FIXED_COST
}
