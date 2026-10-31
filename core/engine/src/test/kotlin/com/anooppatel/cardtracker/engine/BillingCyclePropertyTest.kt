package com.anooppatel.cardtracker.engine

import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.localDate
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Rules that must hold for every date and every card setup.
class BillingCyclePropertyTest {

    private val dates = Arb.localDate(LocalDate.of(2020, 1, 1), LocalDate.of(2035, 12, 31))
    private val statementDays = Arb.int(1..31)
    private val dueOffsets = Arb.int(1..60)

    @Test
    fun `the spend date is inside its own cycle`() = runTest {
        checkAll(dates, statementDays, dueOffsets) { date, day, offset ->
            val cycle = cycleFor(date, BillingConfig(day, offset))
            (date >= cycle.start && date <= cycle.statementDate) shouldBe true
        }
    }

    @Test
    fun `cycles follow each other with no gap and no overlap`() = runTest {
        checkAll(dates, statementDays, dueOffsets) { date, day, offset ->
            val config = BillingConfig(day, offset)
            val cycle = cycleFor(date, config)

            val dayAfter = cycle.statementDate.plusDays(1)
            cycleFor(dayAfter, config).start shouldBe dayAfter

            val dayBefore = cycle.start.minusDays(1)
            cycleFor(dayBefore, config).statementDate shouldBe dayBefore
        }
    }

    @Test
    fun `every day of a cycle maps to that same cycle`() = runTest {
        checkAll(dates, statementDays, dueOffsets) { date, day, offset ->
            val config = BillingConfig(day, offset)
            val cycle = cycleFor(date, config)
            cycleFor(cycle.start, config) shouldBe cycle
            cycleFor(cycle.statementDate, config) shouldBe cycle
        }
    }

    @Test
    fun `a cycle is 28 to 31 days long`() = runTest {
        checkAll(dates, statementDays, dueOffsets) { date, day, offset ->
            val cycle = cycleFor(date, BillingConfig(day, offset))
            val days = ChronoUnit.DAYS.between(cycle.start, cycle.statementDate) + 1
            days shouldBeInRange 28L..31L
        }
    }

    @Test
    fun `the due date is the statement date plus the offset`() = runTest {
        checkAll(dates, statementDays, dueOffsets) { date, day, offset ->
            val cycle = cycleFor(date, BillingConfig(day, offset))
            cycle.dueDate shouldBe cycle.statementDate.plusDays(offset.toLong())
        }
    }
}