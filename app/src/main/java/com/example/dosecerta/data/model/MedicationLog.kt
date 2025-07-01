package com.example.dosecerta.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

enum class LogStatus {
    TAKEN, MISSED, SKIPPED // Added SKIPPED for intentional non-taking
}

@Entity(
    tableName = "medication_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE // Keep logs even if medication is deleted? Maybe SET_NULL or restrict? Let's use CASCADE for now.
        )
        // We could link to Reminder ID as well, but linking to Medication ID is probably sufficient
    ],
    indices = [
        Index(value = ["medicationId"]), 
        Index(value=["logTimestamp"]),
        Index(value = ["medicationId", "scheduledTime"], unique = true) // Prevent duplicate logs for same medication at same scheduled time
    ]
)
data class MedicationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val medicationId: Int,
    val medicationName: String, // Denormalize name for easier display in history
    val dosage: String, // Denormalize dosage info
    val scheduledTime: Date?, // The time it was scheduled (if applicable, from reminder)
    val logTimestamp: Date, // The actual time the log entry was created (when marked taken/missed)
    val status: LogStatus
) 