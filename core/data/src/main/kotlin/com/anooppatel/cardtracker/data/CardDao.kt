package com.anooppatel.cardtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Reads and writes the cards table. Room generates the implementation at compile time. */
@Dao
interface CardDao {

    /** Adds a card and returns its new id. Fails if the same bank + last 4 already exists. */
    @Insert
    suspend fun insert(card: CardEntity): Long

    @Update
    suspend fun update(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun byId(id: Long): CardEntity?

    /** One card, re-emitted when it changes. Null if it doesn't exist (or was deleted). */
    @Query("SELECT * FROM cards WHERE id = :id")
    fun observeById(id: Long): Flow<CardEntity?>

    /** All cards, re-emitted every time the table changes. The home screen will collect this. */
    @Query("SELECT * FROM cards ORDER BY id")
    fun observeAll(): Flow<List<CardEntity>>
}