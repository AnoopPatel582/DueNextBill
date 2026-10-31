package com.anooppatel.cardtracker.engine

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BillStatusPropertyTest {

    private val octBill = cycleFor(LocalDate.of(2026, 10, 8), BillingConfig(statementDay = 8, dueOffsetDays = 20))
    private val days = Arb.localDate(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 12, 31)) // after the statement
    private val payments = Arb.list(
        Arb.long(1L..1_000_000L).map { LedgerEntry(EntryType.PAYMENT, Paise(it), LocalDate.of(2026, 10, 15)) },
        0..5,
    )

    @Test
    fun `remaining is what is left after paying, never below zero`() = runTest {
        checkAll(Arb.long(-1_000_000L..1_000_000L).map(::Paise), payments, days) { due, entries, today ->
            val status = paymentStatus(octBill, due, entries, today)

            status.remaining shouldBe maxOf(due - status.paid, Paise.ZERO)
        }
    }

    @Test
    fun `a bill is paid exactly when nothing remains`() = runTest {
        checkAll(Arb.long(-1_000_000L..1_000_000L).map(::Paise), payments, days) { due, entries, today ->
            val status = paymentStatus(octBill, due, entries, today)

            (status.status == PaymentStatus.PAID) shouldBe (status.remaining == Paise.ZERO)
        }
    }
}