package com.dosecerta.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.domain.DosePolicy
import com.dosecerta.domain.DoseState
import java.util.UUID
import java.time.Instant
import java.time.ZoneId

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
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Schedule::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("medicationId"), Index("scheduleId"), Index("scheduledTime"), Index("originalDueAt"), Index("state"), Index(value = ["occurrenceId"], unique = true)]
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
    val customMedicationName: String? = null,
    val originalDueAt: Long = scheduledTime,
    val scheduleVersion: Long = 1,
    val originalLocalDateTime: String = Instant.ofEpochMilli(originalDueAt).atZone(ZoneId.systemDefault()).toLocalDateTime().withSecond(0).withNano(0).toString(),
    val originalZoneId: String = ZoneId.systemDefault().id,
    val occurrenceId: String = if (scheduleId != null && !isExtraDose) "dose:$scheduleId:$scheduleVersion:$originalLocalDateTime" else "extra:${UUID.randomUUID()}",
    val state: DoseState = when (status) {
        MedicationStatus.TAKEN -> DoseState.TAKEN
        MedicationStatus.SKIPPED -> DoseState.SKIPPED
        MedicationStatus.MISSED -> DoseState.MISSED
        MedicationStatus.PENDING -> DoseState.PENDING
    },
    val deadlineAt: Long = originalDueAt + DosePolicy.TOLERANCE_MILLIS,
    val snoozedUntil: Long? = null,
    val deliveredAt: Long? = null,
    val reminderAt: Long? = null,
    val schedulerHandle: String? = null,
    /** Persists eligibility even if a retained history row loses its parent on deletion. */
    val isScheduledDose: Boolean = scheduleId != null && !isExtraDose,
    val snapshotName: String? = customMedicationName,
    val snapshotDosage: String? = null,
    val snapshotUnit: String? = null,
    val snapshotColor: Int? = null,
    val snapshotForm: PharmaceuticalForm? = null,
    val snapshotFrequency: Frequency? = null,
    val snapshotNotes: String? = null,
    /** LEGACY_V3_RECONSTRUCTED is intentionally distinguished from capture at occurrence creation. */
    val snapshotOrigin: String = "CAPTURED"
)

// Type converter for MedicationStatus
class MedicationLogTypeConverters {
    @TypeConverter
    fun fromDoseState(state: DoseState): String = state.name

    @TypeConverter
    fun toDoseState(value: String): DoseState = DoseState.valueOf(value)
    @TypeConverter
    fun fromMedicationStatus(status: MedicationStatus): String {
        return status.name
    }
    
    @TypeConverter
    fun toMedicationStatus(value: String): MedicationStatus {
        return MedicationStatus.valueOf(value)
    }
}
