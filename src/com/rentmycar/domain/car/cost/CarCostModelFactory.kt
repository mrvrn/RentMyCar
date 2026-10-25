package com.rentmycar.domain.car.cost

import com.rentmycar.domain.car.Car
import com.rentmycar.domain.car.CarCategory
import com.rentmycar.domain.car.dto.CarDto
import java.math.BigDecimal

/** Creates the polymorphic cost model represented by an Exposed car entity. */
fun Car.toCostModel(): CarCostModel =
    costModelFor(CarCategory.fromDatabase(category), consumptionPer100Km)

/** Creates the polymorphic cost model represented by a car DTO. */
fun CarDto.toCostModel(): CarCostModel =
    costModelFor(category, consumptionPer100Km)

private fun costModelFor(category: CarCategory, consumptionPer100Km: BigDecimal?): CarCostModel {
    val consumption = requirePresent(consumptionPer100Km, "Car has no consumption data")
    return when (category) {
        CarCategory.ICE -> IceCostModel(consumption)
        CarCategory.BEV -> BevCostModel(consumption)
        CarCategory.FCEV -> FcevCostModel(consumption)
    }
}

/** Requires a value while preserving the concrete generic type for car validation. */
private fun <T> requirePresent(value: T?, message: String): T =
    value ?: throw IllegalArgumentException(message)
