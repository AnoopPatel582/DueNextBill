package com.anooppatel.cardtracker.engine

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MayMovePropertyTest {

    private val novBill = cycleFor(LocalDate.of(2026, 11, 8), BillingConfig(statementDay = 8, dueOffsetDays = 20))
    private val entries = Arb.list(
        Arb.bind(
            Arb.enum<EntryType>(),
            Arb.long(1L..1_000_000L).map(::Paise),
            Arb.localDate(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31)),
        ) { type, amount, date -> LedgerEntry(type, amount, date) },
        0..30,
    )
    private val lags = Arb.int(0..40)

    @Test
    fun `only spends inside the cycle, near its end, are flagged`() = runTest {
        checkAll(entries, lags) { all, lag ->
            spendsThatMayMove(novBill, all, lag).forEach {
                it.type shouldBe EntryType.SPEND
                (it.date >= novBill.start && it.date <= novBill.statementDate) shouldBe true
                (it.date > novBill.statementDate.minusDays(lag.toLong())) shouldBe true
            }
        }
    }

    @Test
    fun `a longer lag never flags fewer spends`() = runTest {
        checkAll(entries, lags) { all, lag ->
            spendsThatMayMove(novBill, all, lag + 1) shouldContainAll spendsThatMayMove(novBill, all, lag)
        }
    }
}