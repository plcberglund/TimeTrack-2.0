package com.timetrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ett arbetspass. Datum lagras som epochDay (LocalDate.toEpochDay). */
@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val company: String,
    val workplace: String,
    val note: String = "",
    val hours: Double,
    val obHours: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** En heldagsmarkering (Ledig/Sjuk/Semester). Max en per dag. */
@Entity(tableName = "day_marks")
data class DayMark(
    @PrimaryKey val date: Long,
    val status: String,
)

enum class DayStatus(val label: String) {
    LEDIG("Ledig"),
    SJUK("Sjuk"),
    SEMESTER("Semester");

    companion object {
        fun fromName(name: String?): DayStatus? =
            entries.firstOrNull { it.name == name }
    }
}

/** Snabbknapp-förslag, sparas per fält. */
@Entity(tableName = "suggestions", primaryKeys = ["field", "value"])
data class Suggestion(
    val field: String,
    val value: String,
    val useCount: Int = 1,
    val lastUsed: Long = System.currentTimeMillis(),
) {
    companion object {
        const val FIELD_COMPANY = "COMPANY"
        const val FIELD_WORKPLACE = "WORKPLACE"
    }
}
