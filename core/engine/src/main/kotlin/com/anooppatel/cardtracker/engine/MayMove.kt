package com.anooppatel.cardtracker.engine

/**
 * How many days a spend can take to post. The SMS gives the date you paid, but the bank bills
 * by the date the merchant settles, which can be a day or two later.
 */
const val DEFAULT_POSTING_LAG_DAYS = 2

/**
 * The spends on [cycle]'s bill that may post after the statement date and land on the next bill instead.
 * These are spends made in the last [postingLagDays] days of the cycle, up to and including the statement day.
 * Payments, credits and fees are posted by the bank itself, so they never move. A spend converted
 * to EMI is not billed directly, so it does not move either.
 */
fun spendsThatMayMove(
    cycle: Cycle,
    entries: List<LedgerEntry>,
    postingLagDays: Int = DEFAULT_POSTING_LAG_DAYS,
): List<LedgerEntry> {
    require(postingLagDays >= 0) { "postingLagDays must not be negative: $postingLagDays" }

    val firstRiskyDay = cycle.statementDate.minusDays(postingLagDays.toLong() - 1)
    return entries.filter {
        it.type == EntryType.SPEND &&
                !it.convertedToEmi &&
                it.date >= maxOf(firstRiskyDay, cycle.start) &&
                it.date <= cycle.statementDate
    }
}

/** The total of [spendsThatMayMove]: how much of the bill is not certain yet. */
fun amountThatMayMove(
    cycle: Cycle,
    entries: List<LedgerEntry>,
    postingLagDays: Int = DEFAULT_POSTING_LAG_DAYS,
): Paise =
    spendsThatMayMove(cycle, entries, postingLagDays)
        .fold(Paise.ZERO) { total, entry -> total + entry.amount }