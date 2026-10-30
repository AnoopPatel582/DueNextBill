package com.anooppatel.cardtracker.engine

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

// Rules that must hold for ANY amount. checkAll runs each one on 1,000 random
// inputs (plus edge cases like 0 and the range limits) and, if one fails,
// shrinks it to the smallest failing amount.
class PaisePropertyTest {

    // ±₹10 lakh crore: far beyond any card, small enough that sums can't overflow.
    private val amounts = Arb.long(-1_000_000_000_000_000L..1_000_000_000_000_000L).map(::Paise)

    @Test
    fun `adding then subtracting gives back the original`() = runTest {
        checkAll(amounts, amounts) { a, b ->
            (a + b) - b shouldBe a
        }
    }

    @Test
    fun `addition order does not matter`() = runTest {
        checkAll(amounts, amounts) { a, b ->
            a + b shouldBe b + a
        }
    }

    @Test
    fun `formatting loses no money`() = runTest {
        checkAll(amounts) { amount ->
            val digitsOnly = amount.formatInr().replace("₹", "").replace(",", "").replace(".", "")
            Paise(digitsOnly.toLong()) shouldBe amount
        }
    }

    @Test
    fun `formatted text always has the Indian shape`() = runTest {
        checkAll(amounts) { amount ->
            // optional minus, ₹, 1-2 digit groups with commas, a 3-digit group, 2 paise digits
            amount.formatInr() shouldMatch Regex("""-?₹((\d{1,2},)?(\d{2},)*\d{3}|\d{1,3})\.\d{2}""")
        }
    }
}
