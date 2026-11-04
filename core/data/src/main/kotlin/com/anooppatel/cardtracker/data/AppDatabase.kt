package com.anooppatel.cardtracker.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app's single database. Every change to a table bumps [version] and needs a migration,
 * so that your data survives app updates.
 *
 * Version history:
 * 1. cards
 * 2. ledger_entries, emi_installments, statement_anchors. Only new tables, so Room writes
 *    the migration itself by comparing schemas/1.json with schemas/2.json.
 */
@Database(
    entities = [
        CardEntity::class,
        LedgerEntryEntity::class,
        EmiInstallmentEntity::class,
        StatementAnchorEntity::class,
    ],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    abstract fun ledgerDao(): LedgerDao
}