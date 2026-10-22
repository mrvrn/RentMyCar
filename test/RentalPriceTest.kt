package com.rentmycar.domain.rental

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
}
