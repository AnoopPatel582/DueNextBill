package com.anooppatel.cardtracker.data

import com.anooppatel.cardtracker.engine.CardSummary
import com.anooppatel.cardtracker.engine.EmiInstallment
import com.anooppatel.cardtracker.engine.LedgerEntry
import com.anooppatel.cardtracker.engine.Paise
import com.anooppatel.cardtracker.engine.Reminder
import com.anooppatel.cardtracker.engine.StatementAnchor
import com.anooppatel.cardtracker.engine.amountThatMayMove
import com.anooppatel.cardtracker.engine.cardSummary
import com.anooppatel.cardtracker.engine.reminderFor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate

/**
 * Everything the home screen shows for one card.
 *
 * [summary], [amountThatMayMove] and [reminder] are null until you enter the card's first
 * statement total: without an anchor the engine has nowhere to start counting from.
 */
data class CardOverview(
    val card: CardEntity,
    val summary: CardSummary?,
    /** The part of the next bill so far that may still slip to the bill after it. */
    val amountThatMayMove: Paise?,
    /** The reminder for the last statement today, if any. */
    val reminder: Reminder?,
)

/**
 * The only door the UI uses to the data: it writes through here, and reads [CardOverview]s that
 * the engine recomputes every time a card, entry, EMI line or statement changes.
 *
 * [clock] says what "today" is. Tests pass a fixed one.
 */
class CardRepository(private val db: AppDatabase, private val clock: Clock = Clock.systemDefaultZone()) {

    private val cards = db.cardDao()
    private val ledger = db.ledgerDao()

    suspend fun addCard(card: CardEntity): Long = cards.insert(card)

    suspend fun addEntry(cardId: Long, entry: LedgerEntry, description: String = ""): Long =
        ledger.insertEntry(entry.toEntity(cardId, description))

    suspend fun deleteEntry(entry: LedgerEntryEntity) = ledger.deleteEntry(entry)

    suspend fun addInstallments(cardId: Long, installments: List<EmiInstallment>) =
        ledger.insertInstallments(installments.map { it.toEntity(cardId) })

    /** Records an official statement total. It becomes the new starting point if it is the latest. */
    suspend fun saveStatement(cardId: Long, anchor: StatementAnchor) = ledger.upsertAnchor(anchor.toEntity(cardId))

    fun observeEntries(cardId: Long): Flow<List<LedgerEntryEntity>> = ledger.observeEntries(cardId)

    /** One card's overview, kept up to date. Emits null if the card doesn't exist. */
    fun observeOverview(cardId: Long): Flow<CardOverview?> =
        combine(
            cards.observeById(cardId),
            ledger.observeEntries(cardId),
            ledger.observeInstallments(cardId),
            ledger.observeLatestAnchor(cardId),
        ) { card, entries, installments, anchor ->
            card?.let { overviewOf(it, entries, installments, anchor) }
        }

    /** Every card's overview, in the order the cards were added. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllOverviews(): Flow<List<CardOverview>> =
        cards.observeAll().flatMapLatest { all ->
            if (all.isEmpty()) {
                flowOf(emptyList())
            } else {
                // A card deleted mid-way emits null for a moment before the card list catches up
                combine(all.map { observeOverview(it.id) }) { overviews -> overviews.filterNotNull() }
            }
        }

    private fun overviewOf(
        card: CardEntity,
        entries: List<LedgerEntryEntity>,
        installments: List<EmiInstallmentEntity>,
        anchor: StatementAnchorEntity?,
    ): CardOverview {
        val today = LocalDate.now(clock)
        // On the statement day itself the new statement isn't final yet, so there's nothing to compute from
        if (anchor == null || !today.isAfter(anchor.statementDate)) return CardOverview(card, null, null, null)

        val engineEntries = entries.map { it.toEngine() }
        val summary = cardSummary(
            anchor = anchor.toEngine(),
            config = card.billingConfig(),
            entries = engineEntries,
            installments = installments.map { it.toEngine() },
            today = today,
        )
        return CardOverview(
            card = card,
            summary = summary,
            amountThatMayMove = amountThatMayMove(summary.nextBillSoFar.cycle, engineEntries),
            reminder = reminderFor(summary.lastStatement),
        )
    }
}