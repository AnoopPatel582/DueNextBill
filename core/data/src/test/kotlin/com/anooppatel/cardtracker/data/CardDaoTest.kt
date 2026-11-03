package com.anooppatel.cardtracker.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anooppatel.cardtracker.engine.BillingConfig
import io.kotest.assertions.throwables.shouldThrow
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

@RunWith(RobolectricTestRunner::class)
class CardDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: CardDao

    // Masked digits, never your real ones
    private val sbi = CardEntity(bank = "SBI", last4 = "1111", nickname = "SBI Card", statementDay = 8, dueOffsetDays = 20)
    private val icici = CardEntity(bank = "ICICI", last4 = "2222", nickname = "ICICI", statementDay = 16, dueOffsetDays = 18)

    @Before
    fun setUp() {
        // A fresh database in memory for every test, so tests never see each other's data
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.cardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a saved card can be read back by its id`() = runTest {
        val id = dao.insert(sbi)

        dao.byId(id) shouldBe sbi.copy(id = id)
    }

    @Test
    fun `an unknown id gives null`() = runTest {
        dao.byId(42).shouldBeNull()
    }

    @Test
    fun `all cards come back in the order they were added`() = runTest {
        val sbiId = dao.insert(sbi)
        val iciciId = dao.insert(icici)

        dao.observeAll().first() shouldContainExactly listOf(sbi.copy(id = sbiId), icici.copy(id = iciciId))
    }

    @Test
    fun `the same bank and last 4 cannot be added twice`() = runTest {
        dao.insert(sbi)

        shouldThrow<SQLiteConstraintException> { dao.insert(sbi.copy(nickname = "Again")) }
    }

    @Test
    fun `an update changes the stored card`() = runTest {
        val id = dao.insert(sbi)

        dao.update(sbi.copy(id = id, statementDay = 10))

        dao.byId(id)!!.statementDay shouldBe 10
    }

    @Test
    fun `a card gives the engine its billing config`() {
        sbi.billingConfig() shouldBe BillingConfig(statementDay = 8, dueOffsetDays = 20)
    }

    @Test
    fun `last 4 must be exactly four digits`() {
        shouldThrow<IllegalArgumentException> { sbi.copy(last4 = "111") }
        shouldThrow<IllegalArgumentException> { sbi.copy(last4 = "11111") }
        shouldThrow<IllegalArgumentException> { sbi.copy(last4 = "11a1") }
    }
}