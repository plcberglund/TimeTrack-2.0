package com.timetrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Shift::class, DayMark::class, Suggestion::class],
    version = 1,
    exportSchema = false,
)
abstract class TimeTrackDatabase : RoomDatabase() {
    abstract fun shiftDao(): ShiftDao
    abstract fun dayMarkDao(): DayMarkDao
    abstract fun suggestionDao(): SuggestionDao

    companion object {
        @Volatile
        private var INSTANCE: TimeTrackDatabase? = null

        fun get(context: Context): TimeTrackDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeTrackDatabase::class.java,
                    "timetrack.db",
                ).build().also { INSTANCE = it }
            }
    }
}
