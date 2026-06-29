package com.timetrack.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class TimeTrackRepository(context: Context) {
    private val db = TimeTrackDatabase.get(context)
    private val shiftDao = db.shiftDao()
    private val dayMarkDao = db.dayMarkDao()
    private val suggestionDao = db.suggestionDao()
    val settings = SettingsStore(context)

    fun observeShifts(start: LocalDate, end: LocalDate): Flow<List<Shift>> =
        shiftDao.observeBetween(start.toEpochDay(), end.toEpochDay())

    fun observeAllShifts(): Flow<List<Shift>> = shiftDao.observeAll()

    fun observeDayMarks(start: LocalDate, end: LocalDate): Flow<List<DayMark>> =
        dayMarkDao.observeBetween(start.toEpochDay(), end.toEpochDay())

    fun observeCompanySuggestions(): Flow<List<Suggestion>> =
        suggestionDao.observe(Suggestion.FIELD_COMPANY)

    fun observeWorkplaceSuggestions(): Flow<List<Suggestion>> =
        suggestionDao.observe(Suggestion.FIELD_WORKPLACE)

    suspend fun saveShift(shift: Shift) {
        if (shift.id == 0L) shiftDao.insert(shift) else shiftDao.update(shift)
        rememberSuggestion(Suggestion.FIELD_COMPANY, shift.company)
        rememberSuggestion(Suggestion.FIELD_WORKPLACE, shift.workplace)
    }

    suspend fun deleteShift(shift: Shift) = shiftDao.delete(shift)

    suspend fun setDayStatus(date: LocalDate, status: DayStatus) =
        dayMarkDao.upsert(DayMark(date.toEpochDay(), status.name))

    suspend fun clearDayStatus(date: LocalDate) =
        dayMarkDao.deleteByDate(date.toEpochDay())

    suspend fun deleteSuggestion(field: String, value: String) {
        suggestionDao.find(field, value)?.let { suggestionDao.delete(it) }
    }

    private suspend fun rememberSuggestion(field: String, rawValue: String) {
        val value = rawValue.trim()
        if (value.isEmpty()) return
        val existing = suggestionDao.find(field, value)
        if (existing == null) {
            suggestionDao.insert(Suggestion(field, value))
        } else {
            suggestionDao.insert(
                existing.copy(useCount = existing.useCount + 1, lastUsed = System.currentTimeMillis())
            )
        }
    }
}
