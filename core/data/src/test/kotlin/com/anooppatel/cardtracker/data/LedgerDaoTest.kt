package com.anooppatel.cardtracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anooppatel.cardtracker.engine.EmiInstallment
import com.anooppatel.cardtracker.engine.EntryType
import com.anooppatel.cardtracker.engine.LedgerEntry
import com.anooppatel.cardtracker.engine.Paise
import com.anooppatel.cardtracker.engine.StatementAnchor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class LedgerDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LedgerDao
    private var sbiId = 0L
    private var iciciId = 0L

    private fun rs(rupees: Long, paise: Int = 0) = Paise.ofRupees(rupees, paise)
    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.ledgerDao()
        sbiId = db.cardDao().insert(CardEntity(bank = "SBI", last4 = "1111", nickname = "SBI", statementDay = 8, dueOffsetDays = 20))
        iciciId = db.cardDao().insert(CardEntity(bank = "ICICI", last4 = "2222", nickname = "ICICI", statementDay = 16, dueOffsetDays = 18))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `an entry survives the round trip to the engine and back`() = runTest {
        val emiSpend = LedgerEntry(EntryType.SPEND, rs(2549, 39), day(10, 10), convertedToEmi = true)

        dao.insertEntry(emiSpend.toEntity(sbiId, description = "Amazon"))

        val stored = dao.observeEntries(sbiId).first().single()
        stored.description shouldBe "Amazon"
        stored.toEngine() shouldBe emiSpend
    }

    @Test
    fun `entries come back oldest first, and only for their own card`() = runTest {
        val late = LedgerEntry(EntryType.SPEND, rs(300), day(10, 20))
        val early = LedgerEntry(EntryType.PAYMENT, rs(3595), day(10, 10))
        dao.insertEntry(late.toEntity(sbiId))
        dao.insertEntry(early.toEntity(sbiId))
        dao.insertEntry(LedgerEntry(EntryType.SPEND, rs(99), day(10, 15)).toEntity(iciciId))

        dao.observeEntries(sbiId).first().map { it.toEngine() } shouldContainExactly listOf(early, late)
    }

    @Test
    fun `a deleted entry is gone`() = runTest {
        val id = dao.insertEntry(LedgerEntry(EntryType.SPEND, rs(300), day(10, 20)).toEntity(sbiId))

        dao.deleteEntry(dao.observeEntries(sbiId).first().single { it.id == id })

        dao.observeEntries(sbiId).first().shouldBeEmpty()
    }

    @Test
    fun `EMI lines come back in bill date order`() = runTest {
        val december = EmiInstallment(day(12, 8), rs(198, 33), rs(55, 86), rs(10, 6))
        val november = EmiInstallment(day(11, 8), rs(196, 40), rs(57, 79), rs(10, 40))

        dao.insertInstallments(listOf(december.toEntity(sbiId), november.toEntity(sbiId)))

        dao.observeInstallments(sbiId).first().map { it.toEngine() } shouldContainExactly listOf(november, december)
    }

    @Test
    fun `the latest anchor is the most recent statement`() = runTest {
        dao.upsertAnchor(StatementAnchor(day(10, 8), rs(3595)).toEntity(sbiId))
        dao.upsertAnchor(StatementAnchor(day(9, 8), rs(1200)).toEntity(sbiId))

        dao.observeLatestAnchor(sbiId).first()!!.toEngine() shouldBe StatementAnchor(day(10, 8), rs(3595))
    }

    @Test
    fun `saving an anchor for the same statement again replaces it`() = runTest {
        dao.upsertAnchor(StatementAnchor(day(10, 8), rs(3500)).toEntity(sbiId))
        dao.upsertAnchor(StatementAnchor(day(10, 8), rs(3595)).toEntity(sbiId)) // corrected a typo

        dao.observeLatestAnchor(sbiId).first()!!.totalDuePaise shouldBe rs(3595).value
    }

    @Test
    fun `a card with no statement yet has no anchor`() = runTest {
        dao.observeLatestAnchor(iciciId).first().shouldBeNull()
    }

    @Test
    fun `deleting a card deletes its entries, EMI lines and anchors`() = runTest {
        dao.insertEntry(LedgerEntry(EntryType.SPEND, rs(300), day(10, 20)).toEntity(sbiId))
        dao.insertInstallments(listOf(EmiInstallment(day(11, 8), rs(1), rs(1), rs(1)).toEntity(sbiId)))
        dao.upsertAnchor(StatementAnchor(day(10, 8), rs(3595)).toEntity(sbiId))

        db.openHelper.writableDatabase.execSQL("DELETE FROM cards WHERE id = $sbiId")

        dao.observeEntries(sbiId).first().shouldBeEmpty()
        dao.observeInstallments(sbiId).first().shouldBeEmpty()
        dao.observeLatestAnchor(sbiId).first().shouldBeNull()
    }

    @Test
    fun `an entry needs an existing card`() = runTest {
        shouldThrow<SQLiteConstraintException> {
            dao.insertEntry(LedgerEntry(EntryType.SPEND, rs(300), day(10, 20)).toEntity(cardId = 999))
        }
    }
}