package com.anooppatel.cardtracker.engine

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
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

// Money is never created or lost between cycles, whatever the entries look like.
class BillPropertyTest {

    private val anchorDate = LocalDate.of(2026, 1, 8)
    private val sbi = BillingConfig(statementDay = 8, dueOffsetDays = 20)
    private val today = LocalDate.of(2026, 12, 31)

    private val amounts = Arb.long(1L..10_000_000L).map(::Paise) // ₹0.01 to ₹1,00,000
    private val entry = Arb.bind(
        Arb.enum<EntryType>(),
        amounts,
        Arb.localDate(LocalDate.of(2025, 12, 1), LocalDate.of(2027, 1, 31)),
        Arb.boolean(),
    ) { type, amount, date, converted ->
        LedgerEntry(type, amount, date, convertedToEmi = converted && type == EntryType.SPEND)
    }
    private val installment = Arb.bind(Arb.int(1..12), amounts, amounts, amounts) { month, p, i, t ->
        EmiInstallment(LocalDate.of(2026, month, 8), p, i, t)
    }

    @Test
    fun `the last closing balance accounts for every rupee after the anchor`() = runTest {
        checkAll(amounts, Arb.list(entry, 0..40), Arb.list(installment, 0..5)) { anchorTotal, entries, installments ->
            val bills = billsSince(StatementAnchor(anchorDate, anchorTotal), sbi, entries, installments, today)
            val lastStatement = bills.last().cycle.statementDate

            // Add up everything directly, without going cycle by cycle
            var expected = anchorTotal
            for (e in entries) {
                if (e.date <= anchorDate || e.date > lastStatement || e.convertedToEmi) continue
                expected = when (e.type) {
                    EntryType.SPEND, EntryType.FEE -> expected + e.amount
                    EntryType.CREDIT, EntryType.PAYMENT -> expected - e.amount
                }
            }
            for (line in installments) {
                // An EMI line on the anchor's own statement is already inside the anchor total
                if (line.billDate > anchorDate && line.billDate <= lastStatement) expected += line.total
            }

            bills.last().closing shouldBe expected
        }
    }

    @Test
    fun `bills chain with no gaps`() = runTest {
        checkAll(Arb.list(entry, 0..20)) { entries ->
            val bills = billsSince(StatementAnchor(anchorDate, Paise.ZERO), sbi, entries, emptyList(), today)

            bills.first().cycle.start shouldBe anchorDate.plusDays(1)
            bills.zipWithNext().forEach { (previous, next) ->
                next.opening shouldBe previous.closing
                next.cycle.start shouldBe previous.cycle.statementDate.plusDays(1)
            }
        }
    }
}