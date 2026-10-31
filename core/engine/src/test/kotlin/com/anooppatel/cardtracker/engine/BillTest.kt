package com.anooppatel.cardtracker.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BillTest {

    private val sbi = BillingConfig(statementDay = 8, dueOffsetDays = 20)
    private val octToNov = cycleFor(LocalDate.of(2026, 10, 9), sbi) // 9 Oct – 8 Nov 2026

    private fun rs(rupees: Long, paise: Int = 0) = Paise.ofRupees(rupees, paise)
    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)
    private fun spend(amount: Paise, date: LocalDate) = LedgerEntry(EntryType.SPEND, amount, date)

    // Your SBI EMI: ₹2,549.39 converted, first line on the 8 Nov 2026 bill (from SBI's schedule PDF)
    private val firstEmi = EmiInstallment(
        billDate = day(11, 8),
        principal = rs(196, 40),
        interest = rs(57, 79),
        tax = rs(10, 40),
    )

    @Test
    fun `EMI line total is principal plus interest plus tax`() {
        firstEmi.total shouldBe rs(264, 59)
    }

    @Test
    fun `sums each kind of entry inside the cycle`() {
        val entries = listOf(
            spend(rs(500), day(10, 9)), // first day of the cycle
            spend(rs(300), day(11, 8)), // statement day: still on this bill
            LedgerEntry(EntryType.FEE, rs(18), day(10, 20)),
            LedgerEntry(EntryType.CREDIT, rs(100), day(10, 25)),
            LedgerEntry(EntryType.PAYMENT, rs(1000), day(10, 10)),
        )

        val bill = billFor(octToNov, opening = rs(1000), entries, installments = emptyList())

        bill.charges shouldBe rs(818)
        bill.credits shouldBe rs(100)
        bill.payments shouldBe rs(1000)
        bill.emi shouldBe Paise.ZERO
        bill.closing shouldBe rs(718) // 1000 - 1000 - 100 + 818
    }

    @Test
    fun `ignores entries outside the cycle`() {
        val entries = listOf(
            spend(rs(999), day(10, 8)), // previous cycle's statement day
            spend(rs(999), day(11, 9)), // next cycle's first day
        )

        billFor(octToNov, Paise.ZERO, entries, emptyList()).closing shouldBe Paise.ZERO
    }

    @Test
    fun `a spend converted to EMI is not billed, only its EMI line is`() {
        val entries = listOf(
            LedgerEntry(EntryType.SPEND, rs(2549, 39), day(10, 10), convertedToEmi = true),
        )

        val bill = billFor(octToNov, Paise.ZERO, entries, listOf(firstEmi))

        bill.charges shouldBe Paise.ZERO
        bill.emi shouldBe rs(264, 59)
    }

    @Test
    fun `an EMI line is billed only on its own statement date`() {
        val decemberLine = firstEmi.copy(billDate = day(12, 8))

        billFor(octToNov, Paise.ZERO, emptyList(), listOf(decemberLine)).emi shouldBe Paise.ZERO
    }

    @Test
    fun `predicts the 8 Nov 2026 SBI bill`() {
        val entries = listOf(
            // Already inside the 8 Oct statement (₹3,595), so ignored
            spend(rs(2399), day(10, 7)), // Claude e-mandate
            spend(rs(407, 90), day(10, 1)), // IRCTC
            // This cycle
            LedgerEntry(EntryType.PAYMENT, rs(3595), day(10, 10)), // paid the 8 Oct bill
            LedgerEntry(EntryType.SPEND, rs(2549, 39), day(10, 10), convertedToEmi = true),
            // Placeholder: your other net spends since 9 Oct. Replace with the real entries.
            spend(rs(2692, 61), day(10, 20)),
        )

        val bills = billsSince(
            anchor = StatementAnchor(day(10, 8), totalDue = rs(3595)),
            config = sbi,
            entries = entries,
            installments = listOf(firstEmi),
            today = day(10, 31),
        )

        bills shouldHaveSize 1
        bills.single().cycle.dueDate shouldBe day(11, 28)
        bills.single().closing shouldBe rs(2957, 20)
    }

    @Test
    fun `each bill opens with the previous bill's closing balance`() {
        val entries = listOf(
            spend(rs(1000), day(10, 15)), // 8 Nov bill
            spend(rs(200), day(11, 15)), // 8 Dec bill
        )

        val bills = billsSince(StatementAnchor(day(10, 8), rs(500)), sbi, entries, emptyList(), today = day(11, 20))

        bills shouldHaveSize 2
        bills[0].closing shouldBe rs(1500) // 500 unpaid + 1000
        bills[1].opening shouldBe rs(1500)
        bills[1].closing shouldBe rs(1700) // so far: the 8 Dec cycle is still running
    }

    @Test
    fun `an overpayment carries forward as a credit`() {
        val entries = listOf(LedgerEntry(EntryType.PAYMENT, rs(600), day(10, 10)))

        val bills = billsSince(StatementAnchor(day(10, 8), rs(500)), sbi, entries, emptyList(), today = day(10, 31))

        bills.single().closing shouldBe -rs(100)
    }

    @Test
    fun `rejects an anchor that is not a statement date`() {
        shouldThrow<IllegalArgumentException> {
            billsSince(StatementAnchor(day(10, 9), rs(500)), sbi, emptyList(), emptyList(), today = day(10, 31))
        }
    }

    @Test
    fun `rejects zero or negative amounts and converted non-spends`() {
        shouldThrow<IllegalArgumentException> { LedgerEntry(EntryType.SPEND, Paise.ZERO, day(10, 10)) }
        shouldThrow<IllegalArgumentException> { LedgerEntry(EntryType.SPEND, -rs(5), day(10, 10)) }
        shouldThrow<IllegalArgumentException> {
            LedgerEntry(EntryType.PAYMENT, rs(5), day(10, 10), convertedToEmi = true)
        }
    }
}