package com.glassbill.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class VoucherStatus { Draft, Confirmed, Cancelled }

enum class PaymentMethod { Cash, Bank, Upi, Card, Other }

data class VoucherLine(
    val itemId: String? = null,
    val itemName: String,
    val quantity: BigDecimal,
    val unit: String,
    val unitPrice: BigDecimal,
    val discountPercent: BigDecimal = BigDecimal.ZERO,
    val taxRate: TaxRate? = null,
    val taxIncludedInPrice: Boolean = false,
) {
    init {
        require(itemName.isNotBlank()) { "An item name is required." }
        require(quantity > BigDecimal.ZERO) { "Quantity must be positive." }
        require(unitPrice >= BigDecimal.ZERO) { "Unit price cannot be negative." }
        require(discountPercent in BigDecimal.ZERO..BigDecimal(100)) { "Discount must be 0-100." }
    }

    fun toBillLine(): BillLine = BillLine(quantity, unitPrice, discountPercent, taxRate)

    fun lineTotal(): LineTotal = BillCalculator.lineTotal(toBillLine(), taxIncludedInPrice)
}

data class Voucher(
    val id: String = UUID.randomUUID().toString(),
    val kind: DocumentKind,
    val number: String,
    val partyId: String? = null,
    val partyName: String = "",
    val date: Instant = Instant.now(),
    val lines: List<VoucherLine> = emptyList(),
    val discountPercent: BigDecimal = BigDecimal.ZERO,
    val roundOff: BigDecimal = BigDecimal.ZERO,
    val paidAmountMinor: Long = 0,
    val dueDate: Instant? = null,
    val status: VoucherStatus = VoucherStatus.Confirmed,
    val notes: String = "",
) {
    init {
        require(number.isNotBlank()) { "A voucher number is required." }
    }

    fun totals(taxIncludedInPrice: Boolean): BillTotal {
        val base = BillCalculator.billTotal(lines.map { it.toBillLine() }, taxIncludedInPrice)
        return BillTotal(
            taxableAmount = base.taxableAmount,
            taxAmount = base.taxAmount,
            totalAmount = base.totalAmount.add(roundOff).setScale(2, java.math.RoundingMode.HALF_UP),
        )
    }

    fun balanceMinor(totalMinor: Long): Long = totalMinor - paidAmountMinor
}

data class PaymentRecord(
    val id: String = UUID.randomUUID().toString(),
    val kind: DocumentKind, // Receipt or Payment
    val number: String,
    val partyId: String,
    val partyName: String = "",
    val amountMinor: Long,
    val method: PaymentMethod = PaymentMethod.Cash,
    val date: Instant = Instant.now(),
    val allocatedVoucherIds: List<String> = emptyList(),
    val notes: String = "",
)

data class StockMovement(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val voucherId: String,
    val quantityDelta: BigDecimal,
    val date: Instant = Instant.now(),
)

data class NumberSeries(
    val kind: DocumentKind,
    val prefix: String = "",
    val nextNumber: Long = 1,
    val padding: Int = 4,
) {
    fun nextDisplay(): String = prefix + nextNumber.toString().padStart(padding, '0')

    fun advance(): NumberSeries = copy(nextNumber = nextNumber + 1)
}