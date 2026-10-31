package com.anooppatel.cardtracker.engine

/** Why we are notifying you about a bill. */
enum class ReminderKind {
    /** The bill was generated yesterday. */
    STATEMENT_READY,

    /** The due date is a few days away. */
    DUE_SOON,

    /** Today is the last day to pay. */
    DUE_TODAY,

    /** The due date has passed and the bill is not fully paid. Repeats every day until it is. */
    OVERDUE,
}

/**
 * When to remind. [daysBeforeDue] are the days before the due date that get a [ReminderKind.DUE_SOON].
 * Default: 3 days, 48 hours and 24 hours before.
 */
data class ReminderConfig(val daysBeforeDue: Set<Int> = setOf(3, 2, 1)) {
    init {
        require(daysBeforeDue.all { it > 0 }) { "daysBeforeDue must all be positive: $daysBeforeDue" }
    }
}

/** One notification to show. [remaining] is what is still left to pay. */
data class Reminder(val kind: ReminderKind, val cycle: Cycle, val remaining: Paise, val daysToDue: Long)

/**
 * The reminder to show today for a bill, or null if there is nothing to say.
 * A bill that is still open or already paid never gets one. At most one reminder per bill per day:
 * if two apply, the more urgent one wins.
 */
fun reminderFor(status: BillStatus, config: ReminderConfig = ReminderConfig()): Reminder? {
    if (status.status == PaymentStatus.OPEN || status.status == PaymentStatus.PAID) return null

    val today = status.cycle.dueDate.minusDays(status.daysToDue)
    val kind = when {
        status.daysToDue < 0 -> ReminderKind.OVERDUE
        status.daysToDue == 0L -> ReminderKind.DUE_TODAY
        status.daysToDue.toInt() in config.daysBeforeDue -> ReminderKind.DUE_SOON
        today == status.cycle.statementDate.plusDays(1) -> ReminderKind.STATEMENT_READY
        else -> return null
    }
    return Reminder(kind, status.cycle, status.remaining, status.daysToDue)
}