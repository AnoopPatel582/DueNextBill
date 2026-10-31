package com.anooppatel.cardtracker.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BillStatusTest {

    private val sbi = BillingConfig(statementDay = 8, dueOffsetDays = 20)
    private val octBill = cycleFor(LocalDate.of(2026, 10, 8), sbi) // statement 8 Oct, due 28 Oct 2026

    private fun rs(rupees: Long, paise: Int = 0) = Paise.ofRupees(rupees, paise)
    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)
    private fun payment(amount: Paise, date: LocalDate) = LedgerEntry(EntryType.PAYMENT, amount, date)

    @Test
    fun `a bill is open until its statement day is over`() {
        val status = paymentStatus(octBill, rs(3595), emptyList(), today = day(10, 8))

        status.status shouldBe PaymentStatus.OPEN
        status.remaining shouldBe rs(3595)
    }

    @Test
    fun `a generated bill with nothing paid is unpaid`() {
        val status = paymentStatus(octBill, rs(3595), emptyList(), today = day(10, 9))

        status.status shouldBe PaymentStatus.UNPAID
        status.remaining shouldBe rs(3595)
        status.daysToDue shouldBe 19
    }

    @Test
    fun `paying some of it makes it partially paid`() {
        val entries = listOf(payment(rs(1000), day(10, 12)))

        val status = paymentStatus(octBill, rs(3595), entries, today = day(10, 20))

        status.status shouldBe PaymentStatus.PARTIALLY_PAID
        status.paid shouldBe rs(1000)
        status.remaining shouldBe rs(2595)
    }

    @Test
    fun `paying all of it, or more, makes it paid`() {
        val exact = listOf(payment(rs(3595), day(10, 10)))
        val more = listOf(payment(rs(4000), day(10, 10)))

        paymentStatus(octBill, rs(3595), exact, today = day(10, 20)).status shouldBe PaymentStatus.PAID
        paymentStatus(octBill, rs(3595), more, today = day(10, 20)).remaining shouldBe Paise.ZERO
    }

    @Test
    fun `a refund after the statement counts towards the bill`() {
        val entries = listOf(
            payment(rs(3000), day(10, 15)),
            LedgerEntry(EntryType.CREDIT, rs(595), day(10, 18)),
        )

        paymentStatus(octBill, rs(3595), entries, today = day(10, 20)).status shouldBe PaymentStatus.PAID
    }

    @Test
    fun `payments before the statement do not count, they are already inside the amount due`() {
        val entries = listOf(payment(rs(3595), day(10, 8)))

        paymentStatus(octBill, rs(3595), entries, today = day(10, 20)).status shouldBe PaymentStatus.UNPAID
    }

    @Test
    fun `payments after today do not count yet`() {
        val entries = listOf(payment(rs(3595), day(10, 25))) // e.g. a scheduled autopay

        paymentStatus(octBill, rs(3595), entries, today = day(10, 20)).status shouldBe PaymentStatus.UNPAID
    }

    @Test
    fun `the due date itself is not overdue, the day after is`() {
        paymentStatus(octBill, rs(3595), emptyList(), today = day(10, 28)).status shouldBe PaymentStatus.UNPAID

        val late = paymentStatus(octBill, rs(3595), emptyList(), today = day(10, 29))
        late.status shouldBe PaymentStatus.OVERDUE
        late.daysToDue shouldBe -1
    }

    @Test
    fun `a partial payment does not save you from overdue`() {
        val entries = listOf(payment(rs(1000), day(10, 12)))

        paymentStatus(octBill, rs(3595), entries, today = day(10, 31)).status shouldBe PaymentStatus.OVERDUE
    }

    @Test
    fun `paying late still makes it paid`() {
        val entries = listOf(payment(rs(3595), day(10, 30)))

        paymentStatus(octBill, rs(3595), entries, today = day(10, 31)).status shouldBe PaymentStatus.PAID
    }

    @Test
    fun `a zero or credit balance has nothing to pay`() {
        paymentStatus(octBill, Paise.ZERO, emptyList(), today = day(10, 31)).status shouldBe PaymentStatus.PAID
        paymentStatus(octBill, -rs(100), emptyList(), today = day(10, 31)).status shouldBe PaymentStatus.PAID
    }

    @Test
    fun `your SBI card today - 8 Oct bill paid, 8 Nov bill building up`() {
        val entries = listOf(
            payment(rs(3595), day(10, 10)),
            LedgerEntry(EntryType.SPEND, rs(2549, 39), day(10, 10), convertedToEmi = true),
            LedgerEntry(EntryType.SPEND, rs(2692, 61), day(10, 20)), // placeholder, as in BillTest
        )
        val emi = EmiInstallment(day(11, 8), principal = rs(196, 40), interest = rs(57, 79), tax = rs(10, 40))

        val summary = cardSummary(StatementAnchor(day(10, 8), rs(3595)), sbi, entries, listOf(emi), today = day(10, 31))

        summary.lastStatement.cycle.statementDate shouldBe day(10, 8)
        summary.lastStatement.status shouldBe PaymentStatus.PAID
        summary.nextBillSoFar.cycle.dueDate shouldBe day(11, 28)
        summary.nextBillSoFar.closing shouldBe rs(2957, 20)
    }

    @Test
    fun `after a new statement, the summary moves on to it`() {
        val entries = listOf(
            payment(rs(500), day(10, 10)),
            LedgerEntry(EntryType.SPEND, rs(1000), day(10, 15)), // on the 8 Nov bill
        )

        val summary = cardSummary(StatementAnchor(day(10, 8), rs(500)), sbi, entries, emptyList(), today = day(11, 10))

        summary.lastStatement.cycle.statementDate shouldBe day(11, 8)
        summary.lastStatement.status shouldBe PaymentStatus.UNPAID
        summary.lastStatement.remaining shouldBe rs(1000)
        summary.nextBillSoFar.cycle.statementDate shouldBe day(12, 8)
    }

    @Test
    fun `a summary needs today to be after the anchor`() {
        shouldThrow<IllegalArgumentException> {
            cardSummary(StatementAnchor(day(10, 8), rs(500)), sbi, emptyList(), emptyList(), today = day(10, 8))
        }
    }

    @Test
    fun `days to due counts across a month boundary`() {
        ChronoUnit.DAYS.between(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 11, 28)) shouldBe -4
    }
}