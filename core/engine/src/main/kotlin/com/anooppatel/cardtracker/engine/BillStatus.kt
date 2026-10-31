package com.anooppatel.cardtracker.engine

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where a bill stands on a given day. */
enum class PaymentStatus {
    /** The cycle is still running. The amount is only "so far". */
    OPEN,

    /** Generated, nothing paid yet, due date not passed. */
    UNPAID,

    /** Generated, some of it paid, due date not passed. */
    PARTIALLY_PAID,

    /** Fully paid (even if late), or there was nothing to pay. */
    PAID,

    /** Due date passed and not fully paid. */
    OVERDUE,
}

/**
 * The payment state of one bill on a given day.
 * [daysToDue] is negative once the due date has passed.
 */
data class BillStatus(
    val cycle: Cycle,
    val status: PaymentStatus,
    val amountDue: Paise,
    val paid: Paise,
    val remaining: Paise,
    val daysToDue: Long,
)

/**
 * How much of a bill of [amountDue] for [cycle] has been paid by [today].
 * Payments and credits dated after the statement date count towards it; earlier ones are already inside [amountDue].
 */
fun paymentStatus(cycle: Cycle, amountDue: Paise, entries: List<LedgerEntry>, today: LocalDate): BillStatus {
    val daysToDue = ChronoUnit.DAYS.between(today, cycle.dueDate)

    // A spend on the statement day is still on this bill, so the bill is not final until the day after.
    if (!today.isAfter(cycle.statementDate)) {
        val soFar = maxOf(amountDue, Paise.ZERO)
        return BillStatus(cycle, PaymentStatus.OPEN, amountDue, Paise.ZERO, soFar, daysToDue)
    }

    val paid = entries
        .filter { it.type == EntryType.PAYMENT || it.type == EntryType.CREDIT }
        .filter { it.date.isAfter(cycle.statementDate) && !it.date.isAfter(today) }
        .fold(Paise.ZERO) { total, entry -> total + entry.amount }
    val remaining = maxOf(amountDue - paid, Paise.ZERO)

    val status = when {
        remaining == Paise.ZERO -> PaymentStatus.PAID
        today.isAfter(cycle.dueDate) -> PaymentStatus.OVERDUE
        paid > Paise.ZERO -> PaymentStatus.PARTIALLY_PAID
        else -> PaymentStatus.UNPAID
    }
    return BillStatus(cycle, status, amountDue, paid, remaining, daysToDue)
}

/** What the home screen shows for one card: the latest statement and the bill that is building up. */
data class CardSummary(val lastStatement: BillStatus, val nextBillSoFar: Bill)

/**
 * The summary for one card on [today]. If no statement has been generated since [anchor],
 * the anchor itself is the last statement.
 */
fun cardSummary(
    anchor: StatementAnchor,
    config: BillingConfig,
    entries: List<LedgerEntry>,
    installments: List<EmiInstallment>,
    today: LocalDate,
): CardSummary {
    require(today.isAfter(anchor.statementDate)) { "today ($today) must be after the anchor's statement date" }

    val bills = billsSince(anchor, config, entries, installments, today)
    val running = bills.last()
    val lastGenerated = bills.getOrNull(bills.size - 2)

    val lastStatement = if (lastGenerated != null) {
        paymentStatus(lastGenerated.cycle, lastGenerated.closing, entries, today)
    } else {
        paymentStatus(cycleFor(anchor.statementDate, config), anchor.totalDue, entries, today)
    }
    return CardSummary(lastStatement, running)
}