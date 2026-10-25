package test

import com.rentmycar.domain.car.CarCategory
import com.rentmycar.domain.car.cost.BevCostModel
import com.rentmycar.domain.car.cost.CarCostModel
import com.rentmycar.domain.car.cost.FcevCostModel
import com.rentmycar.domain.car.cost.IceCostModel
import com.rentmycar.domain.car.cost.toCostModel
import com.rentmycar.domain.car.dto.CarDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CarCostModelTest {
    @Test
    fun `consumption cost calculates happy path`() {
        assertEquals("0.40".toBigDecimal(), IceCostModel("20".toBigDecimal()).calculateConsumptionCostPerKm())
    }

    @Test
    fun `consumption cost handles zero consumption`() {
        assertEquals("0.00".toBigDecimal(), BevCostModel("0".toBigDecimal()).calculateConsumptionCostPerKm())
    }

    @Test
    fun `consumption cost rounds half up`() {
        assertEquals("0.03".toBigDecimal(), FcevCostModel("0.333".toBigDecimal()).calculateConsumptionCostPerKm())
    }

    @Test
    fun `tco calculates annual fixed and consumption costs`() {
        val model = IceCostModel("20".toBigDecimal())
        assertEquals("1600.00".toBigDecimal(), model.calculateTco(1000))
    }

    @Test
    fun `tco handles zero kilometres`() {
        assertEquals("900.00".toBigDecimal(), BevCostModel("20".toBigDecimal()).calculateTco(0))
    }

    @Test
    fun `tco rejects negative kilometres`() {
        assertFailsWith<IllegalArgumentException> {
            IceCostModel("20".toBigDecimal()).calculateTco(-1)
        }
    }

    @Test
    fun `factory creates the model for every category`() {
        fun car(category: CarCategory) = CarDto(
            1, 1, "Brand", "Type", "PLATE", "10.00".toBigDecimal(),
            1, 1, null, true, category, null, "10.00".toBigDecimal()
        )

        assertIs<IceCostModel>(car(CarCategory.ICE).toCostModel())
        assertIs<BevCostModel>(car(CarCategory.BEV).toCostModel())
        assertIs<FcevCostModel>(car(CarCategory.FCEV).toCostModel())
    }

    @Test
    fun `factory rejects missing consumption`() {
        val car = CarDto(
            1, 1, "Brand", "Type", "PLATE", "10.00".toBigDecimal(),
            1, 1, null, true, CarCategory.ICE, null, null
        )
        assertFailsWith<IllegalArgumentException> { car.toCostModel() }
    }
}
