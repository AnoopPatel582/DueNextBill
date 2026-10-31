package com.anooppatel.cardtracker.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MayMoveTest {

    private val sbi = BillingConfig(statementDay = 8, dueOffsetDays = 20)
    private val novBill = cycleFor(LocalDate.of(2026, 11, 8), sbi) // 9 Oct – 8 Nov 2026

    private fun rs(rupees: Long, paise: Int = 0) = Paise.ofRupees(rupees, paise)
    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)
    private fun spend(amount: Paise, date: LocalDate) = LedgerEntry(EntryType.SPEND, amount, date)

    @Test
    fun `spends on the statement day and the day before may move`() {
        val onStatementDay = spend(rs(300), day(11, 8))
        val dayBefore = spend(rs(200), day(11, 7))
        val twoDaysBefore = spend(rs(100), day(11, 6))

        spendsThatMayMove(novBill, listOf(onStatementDay, dayBefore, twoDaysBefore)) shouldContainExactly
                listOf(onStatementDay, dayBefore)
    }

    @Test
    fun `the amount that may move is the total of those spends`() {
        val entries = listOf(
            spend(rs(300), day(11, 8)),
            spend(rs(200, 50), day(11, 7)),
            spend(rs(100), day(11, 6)),
        )

        amountThatMayMove(novBill, entries) shouldBe rs(500, 50)
    }

    @Test
    fun `payments, credits, fees and EMI conversions never move`() {
        val entries = listOf(
            LedgerEntry(EntryType.PAYMENT, rs(1000), day(11, 8)),
            LedgerEntry(EntryType.CREDIT, rs(50), day(11, 8)),
            LedgerEntry(EntryType.FEE, rs(18), day(11, 8)),
            LedgerEntry(EntryType.SPEND, rs(2549, 39), day(11, 8), convertedToEmi = true),
        )

        spendsThatMayMove(novBill, entries).shouldBeEmpty()
    }

    @Test
    fun `spends from the next cycle are not this bill's concern`() {
        spendsThatMayMove(novBill, listOf(spend(rs(300), day(11, 9)))).shouldBeEmpty()
    }

    @Test
    fun `a longer lag widens the window`() {
        val entries = listOf(spend(rs(100), day(11, 6)))

        spendsThatMayMove(novBill, entries, postingLagDays = 3) shouldContainExactly entries
    }

    @Test
    fun `a lag of zero means nothing moves`() {
        spendsThatMayMove(novBill, listOf(spend(rs(300), day(11, 8))), postingLagDays = 0).shouldBeEmpty()
    }

    @Test
    fun `a huge lag never reaches outside the cycle`() {
        val previousCycle = spend(rs(999), day(10, 8))
        val firstDay = spend(rs(100), day(10, 9))

        spendsThatMayMove(novBill, listOf(previousCycle, firstDay), postingLagDays = 60) shouldContainExactly
                listOf(firstDay)
    }

    @Test
    fun `rejects a negative lag`() {
        shouldThrow<IllegalArgumentException> { spendsThatMayMove(novBill, emptyList(), postingLagDays = -1) }
    }
}