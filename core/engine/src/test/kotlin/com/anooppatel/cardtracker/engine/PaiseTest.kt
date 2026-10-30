package com.anooppatel.cardtracker.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PaiseTest {

    @Test
    fun `ofRupees converts rupees and paise into paise`() {
        Paise.ofRupees(3595) shouldBe Paise(359500)
        Paise.ofRupees(2549, 39) shouldBe Paise(254939)
    }

    @Test
    fun `ofRupees rejects paise outside 0 to 99`() {
        shouldThrow<IllegalArgumentException> { Paise.ofRupees(1, 100) }
        shouldThrow<IllegalArgumentException> { Paise.ofRupees(1, -1) }
    }

    @Test
    fun `adds and subtracts exactly`() {
        // SBI 8 Oct bill: Claude e-mandate + IRCTC + the rest
        val bill = Paise.ofRupees(2399) + Paise.ofRupees(407, 90) + Paise.ofRupees(788, 10)
        bill shouldBe Paise.ofRupees(3595)
        bill - Paise.ofRupees(3595) shouldBe Paise.ZERO
    }

    @Test
    fun `can go negative after an overpayment`() {
        Paise.ofRupees(100) - Paise.ofRupees(150) shouldBe Paise(-5000)
        -Paise.ofRupees(50) shouldBe Paise(-5000)
    }

    @Test
    fun `compares by amount`() {
        (Paise.ofRupees(10) < Paise.ofRupees(11)) shouldBe true
        (Paise(-1) < Paise.ZERO) shouldBe true
        listOf(Paise(300), Paise(-5), Paise(100)).sorted() shouldBe listOf(Paise(-5), Paise(100), Paise(300))
    }

    @Test
    fun `overflow throws instead of silently wrapping`() {
        shouldThrow<ArithmeticException> { Paise(Long.MAX_VALUE) + Paise(1) }
        shouldThrow<ArithmeticException> { Paise(Long.MIN_VALUE) - Paise(1) }
    }

    // One test, run once per row. '|' separates columns because the expected text contains commas.
    @ParameterizedTest(name = "{0} paise -> {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "0          | ₹0.00",
            "5          | ₹0.05",
            "40790      | ₹407.90",
            "359500     | ₹3,595.00",
            "9999999    | ₹99,999.99",
            "10000000   | ₹1,00,000.00",
            "1000000000 | ₹1,00,00,000.00",
            "-239900    | -₹2,399.00",
            "-5         | -₹0.05",
        ],
    )
    fun `formats with Indian digit grouping`(paise: Long, expected: String) {
        Paise(paise).formatInr() shouldBe expected
    }

    @Test
    fun `formats the most negative Long without overflowing`() {
        Paise(Long.MIN_VALUE).formatInr() shouldBe "-₹92,23,37,20,36,85,47,758.08"
    }
}
