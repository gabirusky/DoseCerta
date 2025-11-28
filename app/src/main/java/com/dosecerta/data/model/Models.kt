package com.dosecerta.data.model

import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.Schedule

/**
 * UI model combining Medication with its schedules for today.
 * Represents a medication that needs to be taken at a specific time.
 */
data class ScheduleItem(
    val medication: Medication,
    val schedule: Schedule,
    val scheduledTime: Long,        // Exact timestamp for today
    val status: MedicationStatus,
    val isPastDue: Boolean = false
)

/**
 * UI model combining medication with its schedule for list display.
 */
data class MedicationWithSchedules(
    val medication: Medication,
    val schedules: List<Schedule>
)

/**
 * Statistics model for history screen.
 */
data class MedicationStatistics(
    val takenCount: Int,
    val missedCount: Int,
    val skippedCount: Int,
    val adherencePercentage: Int
)
