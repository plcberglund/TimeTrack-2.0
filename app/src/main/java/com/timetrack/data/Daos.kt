package com.timetrack.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShiftDao {
    @Query("SELECT * FROM shifts WHERE date BETWEEN :start AND :end ORDER BY date ASC, createdAt ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<Shift>>

    @Query("SELECT * FROM shifts ORDER BY date ASC, createdAt ASC")
    fun observeAll(): Flow<List<Shift>>

    @Insert
    suspend fun insert(shift: Shift): Long

    @Update
    suspend fun update(shift: Shift)

    @Delete
    suspend fun delete(shift: Shift)
}

@Dao
interface DayMarkDao {
    @Query("SELECT * FROM day_marks WHERE date BETWEEN :start AND :end")
    fun observeBetween(start: Long, end: Long): Flow<List<DayMark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mark: DayMark)

    @Query("DELETE FROM day_marks WHERE date = :date")
    suspend fun deleteByDate(date: Long)
}

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestions WHERE field = :field ORDER BY useCount DESC, lastUsed DESC")
    fun observe(field: String): Flow<List<Suggestion>>

    @Query("SELECT * FROM suggestions WHERE field = :field AND value = :value LIMIT 1")
    suspend fun find(field: String, value: String): Suggestion?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(suggestion: Suggestion)

    @Delete
    suspend fun delete(suggestion: Suggestion)
}
