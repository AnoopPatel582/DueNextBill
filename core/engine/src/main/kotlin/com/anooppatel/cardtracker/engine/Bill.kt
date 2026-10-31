package com.anooppatel.cardtracker.engine

import java.time.LocalDate

/**
 * A bill for one cycle, computed the way a bank statement does it:
 *
 *   closing = opening - payments - credits + charges + emi
 *
 * [opening] is the previous statement's closing balance. A negative [closing]
 * means you overpaid and the card is in credit. For the cycle that is still
 * running, the amounts are "so far".
 */
data class Bill(
    val cycle: Cycle,
    val opening: Paise,
    val charges: Paise,
    val credits: Paise,
    val payments: Paise,
    val emi: Paise,
) {
    val closing: Paise get() = opening - payments - credits + charges + emi
}

/** An official statement total we trust. Bills are computed forward from it ("observed beats computed"). */
data class StatementAnchor(val statementDate: LocalDate, val totalDue: Paise)

/** The bill for [cycle]: sums the entries dated inside it and the EMI lines billed on its statement date. */
fun billFor(
    cycle: Cycle,
    opening: Paise,
    entries: List<LedgerEntry>,
    installments: List<EmiInstallment>,
): Bill {
    val inCycle = entries.filter { it.date >= cycle.start && it.date <= cycle.statementDate }

    fun sumOf(vararg types: EntryType): Paise =
        inCycle
            .filter { it.type in types && !it.convertedToEmi }
            .fold(Paise.ZERO) { total, entry -> total + entry.amount }

    return Bill(
        cycle = cycle,
        opening = opening,
        charges = sumOf(EntryType.SPEND, EntryType.FEE),
        credits = sumOf(EntryType.CREDIT),
        payments = sumOf(EntryType.PAYMENT),
        emi = installments
            .filter { it.billDate == cycle.statementDate }
            .fold(Paise.ZERO) { total, line -> total + line.total },
    )
}

/**
 * Every bill after [anchor], up to and including the cycle that contains [today].
 * Each bill's closing balance becomes the next bill's opening balance.
 * Entries on or before the anchor's statement date are already inside its total, so they are ignored.
 */
fun billsSince(
    anchor: StatementAnchor,
    config: BillingConfig,
    entries: List<LedgerEntry>,
    installments: List<EmiInstallment>,
    today: LocalDate,
): List<Bill> {
    require(cycleFor(anchor.statementDate, config).statementDate == anchor.statementDate) {
        "${anchor.statementDate} is not a statement date for $config"
    }

    val bills = mutableListOf<Bill>()
    var opening = anchor.totalDue
    var cycle = cycleFor(anchor.statementDate.plusDays(1), config)

    while (!cycle.start.isAfter(today)) {
        val bill = billFor(cycle, opening, entries, installments)
        bills += bill
        opening = bill.closing
        cycle = cycleFor(cycle.statementDate.plusDays(1), config)
    }
    return bills
}