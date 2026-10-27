package com.glassbill.domain

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class CompanyId(val value: String) {
    init {
        require(value.isNotBlank()) { "A company id is required." }
    }
}

enum class PartyRole {
    Customer,
    Supplier,
    Both,
}

enum class DocumentKind {
    Sale,
    Purchase,
    Receipt,
    Payment,
}

data class TaxRate(
    val label: String,
    val percent: BigDecimal,
) {
    init {
        require(label.isNotBlank()) { "A tax label is required." }
        require(percent >= BigDecimal.ZERO) { "A tax rate cannot be negative." }
    }
}

data class MeasurementUnit(
    val id: String,
    val name: String,
    val symbol: String,
) {
    init {
        require(id.isNotBlank()) { "A unit id is required." }
        require(name.isNotBlank()) { "A unit name is required." }
        require(symbol.isNotBlank()) { "A unit symbol is required." }
    }
}

data class Party(
    val id: String,
    val name: String,
    val role: PartyRole,
    val openingBalanceMinor: Long = 0,
    val paymentTermDays: Int? = null,
) {
    init {
        require(id.isNotBlank()) { "A party id is required." }
        require(name.isNotBlank()) { "A party name is required." }
        require(paymentTermDays == null || paymentTermDays >= 0) { "Payment term cannot be negative." }
    }
}

data class BusinessProfile(
    val displayName: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val taxRegistration: String = "",
) {
    init {
        require(displayName.isNotBlank()) { "A business name is required." }
    }
}

enum class ItemKind { Goods, Service }

data class CatalogItem(
    val id: String,
    val name: String,
    val itemKind: ItemKind,
    val unitId: String? = null,
    val taxRateId: String? = null,
    val openingQuantity: BigDecimal = BigDecimal.ZERO,
    val salePriceMinor: Long? = null,
    val purchasePriceMinor: Long? = null,
) {
    init {
        require(id.isNotBlank()) { "An item id is required." }
        require(name.isNotBlank()) { "An item name is required." }
        require(openingQuantity >= BigDecimal.ZERO) { "Opening quantity cannot be negative." }
    }
}

data class UnitConversion(
    val primaryUnit: String,
    val secondaryUnit: String,
    val secondaryUnitsPerPrimary: BigDecimal,
) {
    init {
        require(primaryUnit.isNotBlank()) { "A primary unit is required." }
        require(secondaryUnit.isNotBlank()) { "A secondary unit is required." }
        require(primaryUnit != secondaryUnit) { "A conversion needs two distinct units." }
        require(secondaryUnitsPerPrimary > BigDecimal.ZERO) { "A unit conversion must be positive." }
    }
}

data class BillLine(
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
    val taxRate: TaxRate? = null,
) {
    init {
        require(quantity > BigDecimal.ZERO) { "A bill line quantity must be positive." }
        require(unitPrice >= BigDecimal.ZERO) { "A unit price cannot be negative." }
        require(discountPercent in BigDecimal.ZERO..BigDecimal(100)) { "Discount must be between 0 and 100." }
    }
}

data class LineTotal(
    val taxableAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val totalAmount: BigDecimal,
)

data class BillTotal(
    val taxableAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val totalAmount: BigDecimal,
)

/**
 * Original GlassBill arithmetic. It is deliberately independent of Android and persistence.
 */
object BillCalculator {
    private val Hundred = BigDecimal(100)

    fun lineTotal(line: BillLine, taxIncludedInPrice: Boolean): LineTotal {
        val discounted = line.quantity
            .multiply(line.unitPrice)
            .multiply(Hundred.subtract(line.discountPercent))
            .divide(Hundred, 8, RoundingMode.HALF_UP)
        val rate = line.taxRate?.percent ?: BigDecimal.ZERO

        val taxable = if (taxIncludedInPrice && rate > BigDecimal.ZERO) {
            discounted.divide(Hundred.add(rate), 8, RoundingMode.HALF_UP).multiply(Hundred)
        } else {
            discounted
        }
        val tax = taxable.multiply(rate).divide(Hundred, 8, RoundingMode.HALF_UP)
        val total = if (taxIncludedInPrice) discounted else taxable.add(tax)

        return LineTotal(
            taxableAmount = taxable.money(),
            taxAmount = tax.money(),
            totalAmount = total.money(),
        )
    }

    fun billTotal(lines: List<BillLine>, taxIncludedInPrice: Boolean): BillTotal {
        val totals = lines.map { lineTotal(it, taxIncludedInPrice) }
        return BillTotal(
            taxableAmount = totals.fold(BigDecimal.ZERO) { sum, line -> sum + line.taxableAmount }.money(),
            taxAmount = totals.fold(BigDecimal.ZERO) { sum, line -> sum + line.taxAmount }.money(),
            totalAmount = totals.fold(BigDecimal.ZERO) { sum, line -> sum + line.totalAmount }.money(),
        )
    }

    private fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP)
}
