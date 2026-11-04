package com.anooppatel.cardtracker.data

import androidx.room.TypeConverter
import java.time.LocalDate

/** Teaches Room to store a [LocalDate] as a number: days since 1 Jan 1970. Numbers sort and compare correctly in SQL. */
class Converters {

    @TypeConverter
    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun fromEpochDay(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
}