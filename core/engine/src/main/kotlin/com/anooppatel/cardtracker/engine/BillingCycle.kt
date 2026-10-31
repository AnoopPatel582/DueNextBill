package com.anooppatel.cardtracker.engine

import java.time.LocalDate

/**
 * When a card's bill is generated and when it must be paid.
 * SBI: statement on the 8th, due 20 days later. ICICI: statement on the 16th, due 18 days later.
 */
data class BillingConfig(val statementDay: Int, val dueOffsetDays: Int) {
    init {
        require(statementDay in 1..31) { "statementDay must be in 1..31: $statementDay" }
        require(dueOffsetDays > 0) { "dueOffsetDays must be positive: $dueOffsetDays" }
    }
}

/** One billing cycle: spends from [start] to [statementDate] (both included) are billed on [statementDate]. */
data class Cycle(val start: LocalDate, val statementDate: LocalDate, val dueDate: LocalDate)

/** The billing cycle that a spend made on [date] belongs to. */
fun cycleFor(date: LocalDate, config: BillingConfig): Cycle {
    val statementThisMonth = statementDateIn(date, config.statementDay)

    // A spend on the statement day itself is still on that bill; after it, it moves to next month's bill.
    val statementDate = if (date.isAfter(statementThisMonth)) {
        statementDateIn(date.plusMonths(1), config.statementDay)
    } else {
        statementThisMonth
    }

    val previousStatement = statementDateIn(statementDate.minusMonths(1), config.statementDay)

    return Cycle(
        start = previousStatement.plusDays(1),
        statementDate = statementDate,
        dueDate = statementDate.plusDays(config.dueOffsetDays.toLong()),
    )
}

/** The statement date in the month of [anyDayInMonth]. Day 31 becomes 30 in April and 28 or 29 in February. */
private fun statementDateIn(anyDayInMonth: LocalDate, statementDay: Int): LocalDate =
    anyDayInMonth.withDayOfMonth(minOf(statementDay, anyDayInMonth.lengthOfMonth()))