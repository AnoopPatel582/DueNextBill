package com.anooppatel.cardtracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Entries, EMI lines and statement anchors: everything the engine needs to compute a card's bills. */
@Dao
interface LedgerDao {

    @Insert
    suspend fun insertEntry(entry: LedgerEntryEntity): Long

    @Delete
    suspend fun deleteEntry(entry: LedgerEntryEntity)

    /** A card's entries, oldest first. */
    @Query("SELECT * FROM ledger_entries WHERE card_id = :cardId ORDER BY date, id")
    fun observeEntries(cardId: Long): Flow<List<LedgerEntryEntity>>

    @Insert
    suspend fun insertInstallments(installments: List<EmiInstallmentEntity>)

    @Query("SELECT * FROM emi_installments WHERE card_id = :cardId ORDER BY bill_date, id")
    fun observeInstallments(cardId: Long): Flow<List<EmiInstallmentEntity>>

    /** Saves a statement total. If the card already has one for that date, it is replaced. */
    @Upsert
    suspend fun upsertAnchor(anchor: StatementAnchorEntity)

    /** The card's most recent official statement, or null before you enter the first one. */
    @Query("SELECT * FROM statement_anchors WHERE card_id = :cardId ORDER BY statement_date DESC LIMIT 1")
    fun observeLatestAnchor(cardId: Long): Flow<StatementAnchorEntity?>
}