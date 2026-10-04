package com.dosecerta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.ColumnInfo
import com.dosecerta.domain.RecurrenceKind

/**
 * Room entity representing a medication schedule (reminder time).
 */
@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId")]
)
data class Schedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val medicationId: Long,
    val timeInMinutes: Int,      // Time of day in minutes since midnight (0-1439)
    val daysOfWeek: List<Int>,   // 1=Sunday, 2=Monday, ..., 7=Saturday (empty = daily)
    val isActive: Boolean = true,
    @ColumnInfo(defaultValue = "'DAILY'") val recurrenceKind: RecurrenceKind = if (daysOfWeek.isEmpty()) RecurrenceKind.DAILY else RecurrenceKind.SELECTED_DAYS,
    @ColumnInfo(defaultValue = "1") val monthDay: Int = 1,
    /** Empty means follow the device time zone, including changes while travelling. */
    @ColumnInfo(defaultValue = "''") val zoneId: String = "",
    @ColumnInfo(defaultValue = "1") val version: Long = 1,
    @ColumnInfo(defaultValue = "0") val validFrom: Long = 0,
    val validUntil: Long? = null
)

// Type converter for List<Int>
class ScheduleTypeConverters {
    @TypeConverter
    fun fromRecurrenceKind(value: RecurrenceKind): String = value.name

    @TypeConverter
    fun toRecurrenceKind(value: String): RecurrenceKind = RecurrenceKind.valueOf(value)
    @TypeConverter
    fun fromIntList(value: List<Int>): String {
        return value.joinToString(",")
    }
    
    @TypeConverter
    fun toIntList(value: String): List<Int> {
        return if (value.isEmpty()) emptyList()
        else value.split(",").map { it.toInt() }
    }
}
