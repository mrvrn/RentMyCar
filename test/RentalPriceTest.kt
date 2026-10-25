package test

import com.rentmycar.domain.rental.calculateRentalPrice
import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RentalPriceTest {
    private val start = LocalDateTime.parse("2026-11-01T10:00:00")

    @Test
    fun `charges hourly rate for the reserved duration`() {
        assertEquals(BigDecimal("25.00"), calculateRentalPrice(
            BigDecimal("12.50"), start, LocalDateTime.parse("2026-11-01T12:00:00")
        ))
        assertEquals(BigDecimal("18.75"), calculateRentalPrice(
            BigDecimal("12.50"), start, LocalDateTime.parse("2026-11-01T11:30:00")
        ))
    }

    @Test
    fun `rounds final amount to cents and handles midnight`() {
        assertEquals(BigDecimal("0.17"), calculateRentalPrice(
            BigDecimal("10.00"), start, LocalDateTime.parse("2026-11-01T10:01:00")
        ))
        assertEquals(BigDecimal("150.00"), calculateRentalPrice(
            BigDecimal("10.00"), start, LocalDateTime.parse("2026-11-02T01:00:00")
        ))
    }

    @Test
    fun `rejects empty or reversed reservations`() {
        assertFailsWith<IllegalArgumentException> {
            calculateRentalPrice(BigDecimal.TEN, start, start)
        }
        assertFailsWith<IllegalArgumentException> {
            calculateRentalPrice(BigDecimal.TEN, start, LocalDateTime.parse("2026-11-01T09:00:00"))
        }
    }

    @Test
    fun `rounds half cents up once on the final amount`() {
        assertEquals(BigDecimal("0.01"), calculateRentalPrice(
            BigDecimal("0.01"), start, LocalDateTime.parse("2026-11-01T10:30:00")
        ))
        assertEquals(BigDecimal("0.00"), calculateRentalPrice(
            BigDecimal("0.01"), start, LocalDateTime.parse("2026-11-01T10:29:59")
        ))
    }

    @Test
    fun `includes seconds and nanoseconds in reserved duration`() {
        assertEquals(BigDecimal("0.02"), calculateRentalPrice(
            BigDecimal("36.00"), start, LocalDateTime.parse("2026-11-01T10:00:01.500000000")
        ))
        assertEquals(BigDecimal("0.01"), calculateRentalPrice(
            BigDecimal("36000000.00"), start, LocalDateTime.parse("2026-11-01T10:00:00.000001")
        ))
    }

    @Test
    fun `uses supplied local clock duration across daylight saving changes`() {
        assertEquals(BigDecimal("20.00"), calculateRentalPrice(
            BigDecimal.TEN, LocalDateTime.parse("2026-03-29T01:00"), LocalDateTime.parse("2026-03-29T03:00")
        ))
        assertEquals(BigDecimal("20.00"), calculateRentalPrice(
            BigDecimal.TEN, LocalDateTime.parse("2026-10-25T01:00"), LocalDateTime.parse("2026-10-25T03:00")
        ))
    }

    @Test
    fun `zero hourly rate produces zero total`() {
        assertEquals(BigDecimal("0.00"), calculateRentalPrice(
            BigDecimal.ZERO, start, LocalDateTime.parse("2026-11-02T10:00")
        ))
    }
}
