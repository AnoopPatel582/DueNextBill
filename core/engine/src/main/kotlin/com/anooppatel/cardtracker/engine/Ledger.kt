package com.anooppatel.cardtracker.engine

import java.time.LocalDate

/** What kind of money movement an entry is. */
enum class EntryType {
    /** A purchase. Adds to the bill. */
    SPEND,

    /** A bank charge (late fee, forex markup, GST on fees). Adds to the bill. */
    FEE,

    /** A refund, reversal or cashback. Reduces the bill. */
    CREDIT,

    /** A bill payment you made. Reduces what you owe. */
    PAYMENT,
}

/**
 * One money movement on a card. [amount] is always positive: the [type] says
 * whether it adds to or reduces the bill.
 *
 * [convertedToEmi] marks a spend that the bank turned into an EMI. Its full amount
 * is no longer billed; the monthly [EmiInstallment]s are billed instead.
 */
data class LedgerEntry(
    val type: EntryType,
    val amount: Paise,
    val date: LocalDate,
    val convertedToEmi: Boolean = false,
) {
    init {
        require(amount > Paise.ZERO) { "amount must be positive: $amount" }
        require(!convertedToEmi || type == EntryType.SPEND) { "only a SPEND can be converted to EMI" }
    }
}

/** One EMI line on a statement, taken from the bank's amortization schedule. */
data class EmiInstallment(
    val billDate: LocalDate,
    val principal: Paise,
    val interest: Paise,
    val tax: Paise,
) {
    val total: Paise get() = principal + interest + tax
}