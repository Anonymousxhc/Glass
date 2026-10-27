package com.glassbill.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MastersValidationTest {
    @Test
    fun `party requires name`() {
        assertFailsWith<IllegalArgumentException> {
            Party(id = "p1", name = "", role = PartyRole.Customer)
        }
    }

    @Test
    fun `unit requires symbol`() {
        assertFailsWith<IllegalArgumentException> {
            MeasurementUnit(id = "u1", name = "Piece", symbol = "")
        }
    }

    @Test
    fun `catalog item rejects negative opening quantity`() {
        assertFailsWith<IllegalArgumentException> {
            CatalogItem(id = "i1", name = "Widget", itemKind = ItemKind.Goods, openingQuantity = java.math.BigDecimal("-1"))
        }
    }

    @Test
    fun `business profile requires name`() {
        assertFailsWith<IllegalArgumentException> {
            BusinessProfile(displayName = "")
        }
    }
}