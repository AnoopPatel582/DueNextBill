package com.anooppatel.cardtracker.engine

import kotlin.math.absoluteValue

/**
 * Formats as Indian rupees: 10000000 paise -> "₹1,00,000.00", -239900 -> "-₹2,399.00".
 *
 * java.text.DecimalFormat can't do this: it supports only one grouping size,
 * while Indian grouping is 3 digits first, then groups of 2.
 */
fun Paise.formatInr(): String {
    val sign = if (value < 0) "-" else ""
    // Split before taking the absolute value: abs(Long.MIN_VALUE) overflows,
    // but abs(Long.MIN_VALUE / 100) and abs(Long.MIN_VALUE % 100) don't.
    val rupees = (value / 100).absoluteValue
    val paise = (value % 100).absoluteValue
    return "$sign₹${groupIndian(rupees.toString())}.${paise.toString().padStart(2, '0')}"
}

/** "10000000" -> "1,00,00,000": the last 3 digits, then groups of 2 from the right. */
private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val lastThree = digits.takeLast(3)
    val rest = digits.dropLast(3)
    // Reverse so chunked(2) groups from the right, then reverse back.
    val pairs = rest.reversed().chunked(2).joinToString(",").reversed()
    return "$pairs,$lastThree"
}
