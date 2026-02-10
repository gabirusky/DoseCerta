package com.dosecerta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.dosecerta.data.model.MedicationStatus

/**
 * Room entity representing a log entry for medication intake.
 */
@Entity(
    tableName = "medication_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Schedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId"), Index("scheduleId"), Index("scheduledTime")]
)
data class MedicationLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val medicationId: Long?,      // Nullable for custom/ad-hoc medications
    val scheduleId: Long?,        // Nullable for extra doses
    val scheduledTime: Long,      // Timestamp when medication was scheduled
    val actualTime: Long? = null, // Timestamp when medication was actually taken (null if not taken)
    val status: MedicationStatus, // TAKEN, SKIPPED, MISSED
    val notes: String? = null,
    
    // Extra dose tracking fields
    val isExtraDose: Boolean = false,        // True if this is an extra/unscheduled dose
    val customMedicationName: String? = null // For ad-hoc medications (e.g., "Tylenol for headache")
)

// Type converter for MedicationStatus
class MedicationLogTypeConverters {
    @TypeConverter
    fun fromMedicationStatus(status: MedicationStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toMedicationStatus(value: String): MedicationStatus {
        return MedicationStatus.valueOf(value)
    }
}
