package com.anooppatel.cardtracker.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.anooppatel.cardtracker.engine.EmiInstallment
import com.anooppatel.cardtracker.engine.EntryType
import com.anooppatel.cardtracker.engine.LedgerEntry
import com.anooppatel.cardtracker.engine.Paise
import com.anooppatel.cardtracker.engine.PaymentStatus
import com.anooppatel.cardtracker.engine.ReminderKind
import com.anooppatel.cardtracker.engine.StatementAnchor
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class CardRepositoryTest {

    private lateinit var db: AppDatabase

    private val sbi = CardEntity(bank = "SBI", last4 = "1111", nickname = "SBI", statementDay = 8, dueOffsetDays = 20)
    private val icici = CardEntity(bank = "ICICI", last4 = "2222", nickname = "ICICI", statementDay = 16, dueOffsetDays = 18)

    private fun rs(rupees: Long, paise: Int = 0) = Paise.ofRupees(rupees, paise)
    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)
    private fun spend(amount: Paise, date: LocalDate) = LedgerEntry(EntryType.SPEND, amount, date)

    /** A repository whose "today" is always [today]. */
    private fun repositoryOn(today: LocalDate): CardRepository {
        val zone = ZoneId.of("Asia/Kolkata")
        return CardRepository(db, Clock.fixed(today.atStartOfDay(zone).toInstant(), zone))
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a card without a statement yet has no summary`() = runTest {
        val repo = repositoryOn(day(10, 31))
        val id = repo.addCard(sbi)

        val overview = repo.observeOverview(id).first()!!

        overview.card shouldBe sbi.copy(id = id)
        overview.summary.shouldBeNull()
        overview.reminder.shouldBeNull()
    }

    @Test
    fun `your SBI card on 31 Oct - last bill paid, next bill building up`() = runTest {
        val repo = repositoryOn(day(10, 31))
        val id = repo.addCard(sbi)
        repo.saveStatement(id, StatementAnchor(day(10, 8), rs(3595)))
        repo.addEntry(id, LedgerEntry(EntryType.PAYMENT, rs(3595), day(10, 10)))
        repo.addEntry(id, LedgerEntry(EntryType.SPEND, rs(2549, 39), day(10, 10), convertedToEmi = true))
        repo.addEntry(id, spend(rs(2692, 61), day(10, 20)), description = "placeholder")
        repo.addInstallments(id, listOf(EmiInstallment(day(11, 8), rs(196, 40), rs(57, 79), rs(10, 40))))

        val overview = repo.observeOverview(id).first()!!

        overview.summary!!.lastStatement.status shouldBe PaymentStatus.PAID
        overview.summary!!.nextBillSoFar.closing shouldBe rs(2957, 20)
        overview.summary!!.nextBillSoFar.cycle.dueDate shouldBe day(11, 28)
        overview.amountThatMayMove shouldBe Paise.ZERO
        overview.reminder.shouldBeNull()
    }

    @Test
    fun `an unpaid bill near its due date comes with a reminder`() = runTest {
        val repo = repositoryOn(day(10, 25))
        val id = repo.addCard(sbi)
        repo.saveStatement(id, StatementAnchor(day(10, 8), rs(3595)))

        val reminder = repo.observeOverview(id).first()!!.reminder!!

        reminder.kind shouldBe ReminderKind.DUE_SOON
        reminder.remaining shouldBe rs(3595)
    }

    @Test
    fun `spends on the last days of the cycle are flagged as may move`() = runTest {
        val repo = repositoryOn(day(11, 8))
        val id = repo.addCard(sbi)
        repo.saveStatement(id, StatementAnchor(day(10, 8), rs(0)))
        repo.addEntry(id, spend(rs(300), day(11, 8)))
        repo.addEntry(id, spend(rs(100), day(11, 1)))

        repo.observeOverview(id).first()!!.amountThatMayMove shouldBe rs(300)
    }

    @Test
    fun `on the day of the latest statement there is no summary yet`() = runTest {
        val repo = repositoryOn(day(11, 8))
        val id = repo.addCard(sbi)
        repo.saveStatement(id, StatementAnchor(day(11, 8), rs(2957, 20)))

        repo.observeOverview(id).first()!!.summary.shouldBeNull()
    }

    @Test
    fun `an unknown card has no overview`() = runTest {
        repositoryOn(day(10, 31)).observeOverview(42).first().shouldBeNull()
    }

    @Test
    fun `all overviews come in the order the cards were added`() = runTest {
        val repo = repositoryOn(day(10, 31))
        repo.observeAllOverviews().first().shouldBeEmpty()

        repo.addCard(sbi)
        repo.addCard(icici)

        repo.observeAllOverviews().first().map { it.card.bank } shouldBe listOf("SBI", "ICICI")
    }

    // Room watches the tables on its own background threads, so this test waits in real time (runBlocking)
    @Test
    fun `adding a spend updates the next bill by itself`() = runBlocking {
        val repo = repositoryOn(day(10, 31))
        val id = repo.addCard(sbi)
        repo.saveStatement(id, StatementAnchor(day(10, 8), rs(0)))

        val emissions = Channel<CardOverview?>(Channel.UNLIMITED)
        val collector = launch { repo.observeOverview(id).collect { emissions.send(it) } }

        withTimeout(5_000) {
            emissions.receive()!!.summary!!.nextBillSoFar.closing shouldBe Paise.ZERO

            repo.addEntry(id, spend(rs(450), day(10, 25)))

            // Nobody asks again: the flow pushes the new total on its own
            var latest = emissions.receive()
            while (latest!!.summary!!.nextBillSoFar.closing == Paise.ZERO) latest = emissions.receive()
            latest.summary!!.nextBillSoFar.closing shouldBe rs(450)
        }
        collector.cancel()
    }
}