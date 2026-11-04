package com.anooppatel.cardtracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.anooppatel.cardtracker.engine.EmiInstallment
import com.anooppatel.cardtracker.engine.EntryType
import com.anooppatel.cardtracker.engine.LedgerEntry
import com.anooppatel.cardtracker.engine.Paise
import com.anooppatel.cardtracker.engine.StatementAnchor
import java.time.LocalDate

// Every row below belongs to one card. Deleting the card deletes its rows too (CASCADE).
// Money is stored as whole paise in a Long column, exactly like Paise itself.

/** One money movement on a card. See [LedgerEntry]. */
@Entity(
    tableName = "ledger_entries",
    foreignKeys = [ForeignKey(CardEntity::class, ["id"], ["card_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("card_id")],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    val type: EntryType,
    @ColumnInfo(name = "amount_paise") val amountPaise: Long,
    val date: LocalDate,
    @ColumnInfo(name = "converted_to_emi") val convertedToEmi: Boolean = false,
    /** Merchant or a note, for you to recognise the entry. The engine ignores it. */
    val description: String = "",
) {
    fun toEngine(): LedgerEntry = LedgerEntry(type, Paise(amountPaise), date, convertedToEmi)
}

/** One EMI line from the bank's schedule. See [EmiInstallment]. */
@Entity(
    tableName = "emi_installments",
    foreignKeys = [ForeignKey(CardEntity::class, ["id"], ["card_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("card_id")],
)
data class EmiInstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "card_id") val cardId: Long,
    @ColumnInfo(name = "bill_date") val billDate: LocalDate,
    @ColumnInfo(name = "principal_paise") val principalPaise: Long,
    @ColumnInfo(name = "interest_paise") val interestPaise: Long,
    @ColumnInfo(name = "tax_paise") val taxPaise: Long,
) {
    fun toEngine(): EmiInstallment =
        EmiInstallment(billDate, Paise(principalPaise), Paise(interestPaise), Paise(taxPaise))
}

/**
 * An official statement total. See [StatementAnchor].
 * The key is (card, statement date): a card has at most one total per statement, and saving it again replaces it.
 */
@Entity(
    tableName = "statement_anchors",
    primaryKeys = ["card_id", "statement_date"],
    foreignKeys = [ForeignKey(CardEntity::class, ["id"], ["card_id"], onDelete = ForeignKey.CASCADE)],
)
data class StatementAnchorEntity(
    @ColumnInfo(name = "card_id") val cardId: Long,
    @ColumnInfo(name = "statement_date") val statementDate: LocalDate,
    @ColumnInfo(name = "total_due_paise") val totalDuePaise: Long,
) {
    fun toEngine(): StatementAnchor = StatementAnchor(statementDate, Paise(totalDuePaise))
}

fun LedgerEntry.toEntity(cardId: Long, description: String = ""): LedgerEntryEntity =
    LedgerEntryEntity(
        cardId = cardId,
        type = type,
        amountPaise = amount.value,
        date = date,
        convertedToEmi = convertedToEmi,
        description = description,
    )

fun EmiInstallment.toEntity(cardId: Long): EmiInstallmentEntity =
    EmiInstallmentEntity(
        cardId = cardId,
        billDate = billDate,
        principalPaise = principal.value,
        interestPaise = interest.value,
        taxPaise = tax.value,
    )

fun StatementAnchor.toEntity(cardId: Long): StatementAnchorEntity =
    StatementAnchorEntity(cardId = cardId, statementDate = statementDate, totalDuePaise = totalDue.value)