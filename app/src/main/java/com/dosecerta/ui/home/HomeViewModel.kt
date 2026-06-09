package com.dosecerta.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.ScheduleItem
import com.dosecerta.data.model.MedicationStatistics
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.util.DateTimeUtils
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen.
 */
class HomeViewModel(
    private val repository: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val context: Context
) : ViewModel() {

    private val settingsPreferences = SettingsPreferences(context)

    // Today's medication schedule — reactively observes both schedules and logs.
    // A11: AS_NEEDED medications are excluded here (they have no schedules, guard is safety-first).
    val todaySchedule: StateFlow<List<ScheduleItem>> = combine(
        repository.getAllActiveSchedules(),
        repository.getAllLogs()
    ) { schedules, allLogs ->
        // Build schedule items for today
        val items = mutableListOf<ScheduleItem>()

        for (schedule in schedules) {
            // Check if this schedule should trigger today
            if (!DateTimeUtils.shouldScheduleToday(schedule.daysOfWeek)) continue

            val medication = repository.getMedicationByIdSync(schedule.medicationId) ?: continue

            // A11: Safety guard — AS_NEEDED medications should never appear here
            if (medication.frequency == Frequency.AS_NEEDED) continue

            val scheduledTime = DateTimeUtils.getTimestampForToday(schedule.timeInMinutes)

            // Find log for this schedule today from the allLogs list
            val log = allLogs.find {
                it.medicationId == medication.id &&
                it.scheduleId == schedule.id &&
                it.scheduledTime == scheduledTime
            }

            val status = log?.status ?: if (scheduledTime < System.currentTimeMillis()) {
                MedicationStatus.MISSED
            } else {
                MedicationStatus.PENDING
            }

            items.add(
                ScheduleItem(
                    medication = medication,
                    schedule = schedule,
                    scheduledTime = scheduledTime,
                    status = status,
                    isPastDue = scheduledTime < System.currentTimeMillis() && status == MedicationStatus.PENDING
                )
            )
        }

        // Sort by time
        items.sortedBy { it.scheduledTime }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Statistics - reactively observes logs
    val statistics: StateFlow<MedicationStatistics> = repository.getAllLogs().map { allLogs ->
        val startOfWeek = DateTimeUtils.getStartOfWeek()
        val now = System.currentTimeMillis()

        // Filter logs within the week range
        val logsThisWeek = allLogs.filter { it.scheduledTime >= startOfWeek && it.scheduledTime <= now }

        val takenCount = logsThisWeek.count { it.status == MedicationStatus.TAKEN }
        val missedCount = logsThisWeek.count { it.status == MedicationStatus.MISSED }
        val skippedCount = logsThisWeek.count { it.status == MedicationStatus.SKIPPED }
        val totalCount = logsThisWeek.size

        val adherence = if (totalCount > 0) {
            ((takenCount.toFloat() / totalCount) * 100).toInt()
        } else {
            100 // Default to 100% if no logs
        }

        MedicationStatistics(
            takenCount = takenCount,
            missedCount = missedCount,
            skippedCount = skippedCount,
            adherencePercentage = adherence
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MedicationStatistics(0, 0, 0, 100)
    )

    /**
     * Mark medication as taken.
     * B4: Cancel any pending missed-reminder alarm when user takes the medication.
     */
    suspend fun markAsTaken(scheduleItem: ScheduleItem) {
        // Check if log already exists
        val existingLog = repository.getLog(
            scheduleItem.medication.id,
            scheduleItem.schedule.id,
            scheduleItem.scheduledTime
        )

        if (existingLog != null) {
            // Update existing log
            repository.updateLog(
                existingLog.copy(
                    status = MedicationStatus.TAKEN,
                    actualTime = System.currentTimeMillis()
                )
            )
        } else {
            // Create new log
            repository.insertLog(
                MedicationLog(
                    medicationId = scheduleItem.medication.id,
                    scheduleId = scheduleItem.schedule.id,
                    scheduledTime = scheduleItem.scheduledTime,
                    actualTime = System.currentTimeMillis(),
                    status = MedicationStatus.TAKEN
                )
            )
        }

        // B4: Cancel missed reminder — user already took it
        alarmScheduler.cancelMissedReminderAlarm(
            scheduleItem.medication.id,
            scheduleItem.schedule.id,
            scheduleItem.scheduledTime
        )
    }

    /**
     * Skip medication — mark as skipped.
     * B3: Schedule a missed-reminder alarm so user gets a follow-up notification.
     */
    suspend fun skipMedication(scheduleItem: ScheduleItem) {
        // Check if log already exists
        val existingLog = repository.getLog(
            scheduleItem.medication.id,
            scheduleItem.schedule.id,
            scheduleItem.scheduledTime
        )

        if (existingLog != null) {
            // Update existing log to mark as skipped
            repository.updateLog(
                existingLog.copy(
                    status = MedicationStatus.SKIPPED,
                    actualTime = System.currentTimeMillis()
                )
            )
        } else {
            // Create new log as skipped
            repository.insertLog(
                MedicationLog(
                    medicationId = scheduleItem.medication.id,
                    scheduleId = scheduleItem.schedule.id,
                    scheduledTime = scheduleItem.scheduledTime,
                    actualTime = System.currentTimeMillis(),
                    status = MedicationStatus.SKIPPED
                )
            )
        }

        // B3: Schedule missed reminder — even a deliberate skip deserves a follow-up nudge
        val reminderHours = settingsPreferences.getMissedReminderHoursSync()
        alarmScheduler.scheduleMissedReminderAlarm(
            scheduleItem.medication.id,
            scheduleItem.schedule.id,
            scheduleItem.scheduledTime,
            reminderHours
        )
    }

    /**
     * Get all active medications for extra dose dialog.
     */
    val activeMedications = repository.getAllActiveMedications()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * A6: AS_NEEDED medications — shown on home screen as tappable dose cards.
     */
    val asNeededMedications = repository.getAllActiveMedications()
        .map { meds -> meds.filter { it.frequency == Frequency.AS_NEEDED } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Record an extra dose for an existing medication.
     */
    suspend fun recordExtraDose(medicationId: Long) {
        repository.recordExtraDose(medicationId)
    }

    /**
     * Record an extra dose for a custom medication.
     */
    suspend fun recordCustomExtraDose(medicationName: String) {
        repository.recordCustomExtraDose(medicationName)
    }
}
