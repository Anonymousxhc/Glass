package com.glassbill

import com.glassbill.domain.BillCalculator
import com.glassbill.domain.BillLine
import com.glassbill.domain.TaxRate
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class BillCalculatorTest {
    @Test
    fun `a local sale total remains deterministic`() {
        val result = BillCalculator.billTotal(
            lines = listOf(
                BillLine(
                    quantity = BigDecimal("3"),
                    unitPrice = BigDecimal("50"),
                    taxRate = TaxRate("Standard", BigDecimal("5")),
                ),
            ),
            taxIncludedInPrice = false,
        )

        assertEquals(BigDecimal("157.50"), result.totalAmount)
    }
}
