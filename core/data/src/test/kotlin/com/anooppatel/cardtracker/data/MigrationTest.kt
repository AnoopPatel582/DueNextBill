package com.anooppatel.cardtracker.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.platform.app.InstrumentationRegistry
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Upgrades a real database file from an old version and checks that the result matches the
 * exported schema exactly, and that your data is still there. One test per migration.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("migration-test"),
        driver = AndroidSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @Test
    fun `version 1 to 2 adds the ledger tables and keeps your cards`() {
        // Version 1, written with plain SQL: the Kotlin classes now describe version 2
        helper.createDatabase(1).use { v1 ->
            v1.execSQL(
                "INSERT INTO cards (bank, last4, nickname, statement_day, due_offset_days) " +
                        "VALUES ('SBI', '1111', 'SBI Card', 8, 20)",
            )
        }

        // Runs the auto-migration, then fails if any table differs from schemas/2.json
        helper.runMigrationsAndValidate(2).use { v2 ->
            v2.prepare("SELECT last4, statement_day FROM cards").use { row ->
                row.step() shouldBe true
                row.getText(0) shouldBe "1111"
                row.getLong(1) shouldBe 8
            }
            v2.execSQL(
                "INSERT INTO ledger_entries (card_id, type, amount_paise, date, converted_to_emi, description) " +
                        "VALUES (1, 'SPEND', 30000, 20746, 0, '')",
            )
        }
    }
}