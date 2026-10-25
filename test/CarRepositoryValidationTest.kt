package test

import com.rentmycar.domain.car.CarRepository
import com.rentmycar.domain.car.dto.CreateCarDto
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CarRepositoryValidationTest {
    @Test
    fun `rejects negative hourly rate before accessing database`() = runBlocking {
        val car = CreateCarDto(1L, "Test", "Car", "TEST-12", BigDecimal("-12.50"), 1L, 1L)
        val error = assertFailsWith<IllegalArgumentException> { CarRepository().create(car) }
        assertEquals("Hourly rate must not be negative", error.message)
    }
}
