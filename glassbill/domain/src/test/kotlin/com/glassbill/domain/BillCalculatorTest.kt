package com.glassbill.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BillCalculatorTest {
    @Test
    fun `exclusive tax is added after discount`() {
        val line = BillLine(
            quantity = BigDecimal("2"),
            unitPrice = BigDecimal("100"),
            discountPercent = BigDecimal("10"),
            taxRate = TaxRate("Standard", BigDecimal("18")),
        )

        assertEquals(BigDecimal("180.00"), BillCalculator.lineTotal(line, false).taxableAmount)
        assertEquals(BigDecimal("32.40"), BillCalculator.lineTotal(line, false).taxAmount)
        assertEquals(BigDecimal("212.40"), BillCalculator.lineTotal(line, false).totalAmount)
    }

    @Test
    fun `inclusive tax is backed out of the entered price`() {
        val line = BillLine(
            quantity = BigDecimal.ONE,
            unitPrice = BigDecimal("118"),
            taxRate = TaxRate("Standard", BigDecimal("18")),
        )

        assertEquals(BigDecimal("100.00"), BillCalculator.lineTotal(line, true).taxableAmount)
        assertEquals(BigDecimal("18.00"), BillCalculator.lineTotal(line, true).taxAmount)
        assertEquals(BigDecimal("118.00"), BillCalculator.lineTotal(line, true).totalAmount)
    }

    @Test
    fun `unit conversion refuses zero or negative factors`() {
        assertFailsWith<IllegalArgumentException> {
            UnitConversion("Box", "Piece", BigDecimal.ZERO)
        }
    }
}
