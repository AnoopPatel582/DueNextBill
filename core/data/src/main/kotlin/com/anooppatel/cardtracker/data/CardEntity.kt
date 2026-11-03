package com.anooppatel.cardtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.anooppatel.cardtracker.engine.BillingConfig

/**
 * One credit card. Only the bank and the last 4 digits are stored: never the full number, CVV or PIN.
 * A bank can't issue two cards with the same last 4 to you, so that pair is unique.
 */
@Entity(tableName = "cards", indices = [Index(value = ["bank", "last4"], unique = true)])
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bank: String,
    val last4: String,
    val nickname: String,
    @ColumnInfo(name = "statement_day") val statementDay: Int,
    @ColumnInfo(name = "due_offset_days") val dueOffsetDays: Int,
) {
    init {
        require(last4.length == 4 && last4.all { it.isDigit() }) { "last4 must be exactly 4 digits" }
    }

    /** The engine's view of this card's billing dates. */
    fun billingConfig(): BillingConfig = BillingConfig(statementDay, dueOffsetDays)
}