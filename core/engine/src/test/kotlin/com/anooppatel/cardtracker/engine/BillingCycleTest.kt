package com.anooppatel.cardtracker.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class BillingCycleTest {

    private val sbi = BillingConfig(statementDay = 8, dueOffsetDays = 20)

    @Test
    fun `SBI spend on 7 Oct lands on the 8 Oct bill`() {
        // Arrange
        val spendDate = LocalDate.of(2026, 10, 7)

        // Act
        val cycle = cycleFor(spendDate, sbi)

        // Assert
        cycle shouldBe Cycle(
            start = LocalDate.of(2026, 9, 9),
            statementDate = LocalDate.of(2026, 10, 8),
            dueDate = LocalDate.of(2026, 10, 28),
        )
    }

    // One row per case. JUnit turns "2026-10-08" into a LocalDate automatically.
    @ParameterizedTest(name = "{0}: spend on {1} (day {2}, +{3}) -> {4} to {5}, due {6}")
    @CsvSource(
        delimiter = '|',
        value = [
            // case                       | spend      | day | due+ | start      | statement  | due
            "SBI statement day itself     | 2026-10-08 | 8   | 20   | 2026-09-09 | 2026-10-08 | 2026-10-28",
            "SBI day after statement      | 2026-10-09 | 8   | 20   | 2026-10-09 | 2026-11-08 | 2026-11-28",
            "SBI crosses the year         | 2026-12-20 | 8   | 20   | 2026-12-09 | 2027-01-08 | 2027-01-28",
            "ICICI statement day itself   | 2026-10-16 | 16  | 18   | 2026-09-17 | 2026-10-16 | 2026-11-03",
            "ICICI day after statement    | 2026-10-17 | 16  | 18   | 2026-10-17 | 2026-11-16 | 2026-12-04",
            "ICICI due date after Feb     | 2027-02-10 | 16  | 18   | 2027-01-17 | 2027-02-16 | 2027-03-06",
            "day 31 clamps to 28 Feb      | 2027-02-15 | 31  | 20   | 2027-02-01 | 2027-02-28 | 2027-03-20",
            "day 31 clamps to 29 Feb leap | 2028-02-15 | 31  | 20   | 2028-02-01 | 2028-02-29 | 2028-03-20",
            "day 31 in a 31-day month     | 2027-03-01 | 31  | 20   | 2027-03-01 | 2027-03-31 | 2027-04-20",
            "day 30 after short Feb       | 2027-03-01 | 30  | 20   | 2027-03-01 | 2027-03-30 | 2027-04-19",
            "day 1 statement day itself   | 2026-10-01 | 1   | 20   | 2026-09-02 | 2026-10-01 | 2026-10-21",
            "day 1 day after statement    | 2026-10-02 | 1   | 20   | 2026-10-02 | 2026-11-01 | 2026-11-21",
        ],
    )
    fun `finds the cycle a spend belongs to`(
        case: String,
        spend: LocalDate,
        statementDay: Int,
        dueOffsetDays: Int,
        start: LocalDate,
        statementDate: LocalDate,
        dueDate: LocalDate,
    ) {
        cycleFor(spend, BillingConfig(statementDay, dueOffsetDays)) shouldBe Cycle(start, statementDate, dueDate)
    }

    @Test
    fun `rejects a statement day outside 1 to 31`() {
        shouldThrow<IllegalArgumentException> { BillingConfig(statementDay = 0, dueOffsetDays = 20) }
        shouldThrow<IllegalArgumentException> { BillingConfig(statementDay = 32, dueOffsetDays = 20) }
    }

    @Test
    fun `rejects a due offset that is not positive`() {
        shouldThrow<IllegalArgumentException> { BillingConfig(statementDay = 8, dueOffsetDays = 0) }
        shouldThrow<IllegalArgumentException> { BillingConfig(statementDay = 8, dueOffsetDays = -1) }
    }
}