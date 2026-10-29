package com.anooppatel.cardtracker.engine

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
}
