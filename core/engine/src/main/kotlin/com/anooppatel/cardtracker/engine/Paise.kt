package com.anooppatel.cardtracker.engine

/**
 * An amount of money in paise (1 rupee = 100 paise).
 *
 * Money is never a Double: in floating point 0.1 + 0.2 != 0.3, so sums drift.
 * Whole paise in a Long are exact. Negative amounts are allowed, e.g. a card
 * balance after an overpayment.
 */
@JvmInline
value class Paise(val value: Long) : Comparable<Paise> {

    // addExact/subtractExact throw ArithmeticException on overflow instead of
    // silently wrapping around to a wrong (often negative) number.
    operator fun plus(other: Paise): Paise = Paise(Math.addExact(value, other.value))

    operator fun minus(other: Paise): Paise = Paise(Math.subtractExact(value, other.value))

    operator fun unaryMinus(): Paise = Paise(Math.negateExact(value))

    override fun compareTo(other: Paise): Int = value.compareTo(other.value)

    companion object {
        val ZERO = Paise(0)

        /** `ofRupees(2549, 39)` is ₹2,549.39. For negative amounts use `-Paise.ofRupees(...)`. */
        fun ofRupees(rupees: Long, paise: Int = 0): Paise {
            require(rupees >= 0) { "rupees must not be negative: $rupees" }
            require(paise in 0..99) { "paise must be in 0..99: $paise" }
            return Paise(Math.addExact(Math.multiplyExact(rupees, 100L), paise.toLong()))
        }
    }
}
