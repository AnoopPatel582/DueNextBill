package com.anooppatel.cardtracker.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's single database. Every change to a table bumps [version] and needs a migration,
 * so that your data survives app updates.
 */
@Database(entities = [CardEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}