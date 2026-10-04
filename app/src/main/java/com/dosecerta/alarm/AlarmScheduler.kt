package com.dosecerta.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import com.dosecerta.domain.DoseState
import com.dosecerta.notification.MarkMissedReceiver
import com.dosecerta.notification.MissedReminderReceiver
import com.dosecerta.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ScheduleResult {
    data class Scheduled(val occurrenceId: String, val at: Long, val exact: Boolean) : ScheduleResult
    data class Unavailable(val reason: String) : ScheduleResult
    data class Failed(val error: Throwable) : ScheduleResult
}

/** All alarm handles are reconstructible from the persisted occurrence identity. */
class AlarmScheduler(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(AlarmManager::class.java)
    private val repository by lazy { MedicationRepository(DoseCertaDatabase.getDatabase(context)) }

    companion object { private val scheduling = Mutex() }

    suspend fun scheduleAlarm(medicationId: Long, schedule: Schedule): ScheduleResult = scheduling.withLock {
        cancelLegacy(medicationId, schedule.id)
        val medication = repository.getMedicationByIdSync(medicationId)
        if (medication?.isActive != true || !schedule.isActive) return@withLock ScheduleResult.Unavailable("inactive")
        val next = repository.previewOccurrences(schedule, 1).firstOrNull() ?: return@withLock ScheduleResult.Unavailable("no_occurrence")
        val occurrence = repository.ensureOccurrence(schedule.id, next.originalDueAt)
            ?: return@withLock ScheduleResult.Unavailable("suppressed")
        scheduleOccurrenceLocked(occurrence)
    }

    suspend fun scheduleAlarmsForMedication(medicationId: Long, schedules: List<Schedule>): List<ScheduleResult> =
        schedules.map { scheduleAlarm(medicationId, it) }

    suspend fun scheduleOccurrence(occurrence: MedicationLog): ScheduleResult = scheduling.withLock {
        scheduleOccurrenceLocked(occurrence)
    }

    private suspend fun scheduleOccurrenceLocked(request: MedicationLog): ScheduleResult {
        val occurrence = repository.getOccurrence(request.occurrenceId) ?: return ScheduleResult.Unavailable("deleted")
        if (!repository.isOccurrenceCurrent(occurrence) || occurrence.state !in pendingStates) {
            cancelOccurrenceHandles(occurrence.occurrenceId)
            return ScheduleResult.Unavailable("stale")
        }
        val due = occurrence.snoozedUntil ?: occurrence.originalDueAt
        val now = System.currentTimeMillis()
        if (occurrence.deadlineAt <= now) {
            cancelOccurrenceHandles(occurrence.occurrenceId)
            val result = DoseActionCoordinator(repository).timeout(occurrence.occurrenceId)
            if (result is DoseActionResult.Success) scheduleFollowUpLocked(result.occurrence)
            return ScheduleResult.Unavailable("expired")
        }
        val kind = if (occurrence.state == DoseState.SNOOZED) AlarmIdentity.SNOOZE else AlarmIdentity.RECURRENCE
        // A boot/startup reconciliation restores future delivery; it never rings old events in a batch.
        val delivery = if (due > now && occurrence.state in setOf(DoseState.PENDING, DoseState.SNOOZED)) {
            scheduleHandle(occurrence.occurrenceId, kind, due, MedicationAlarmReceiver::class.java)
        } else ScheduleResult.Unavailable("delivery_already_due")
        val timeout = scheduleHandle(occurrence.occurrenceId, AlarmIdentity.TIMEOUT, occurrence.deadlineAt, MarkMissedReceiver::class.java)
        val description = "v2|$kind:$due|timeout:${occurrence.deadlineAt}|${(delivery as? ScheduleResult.Scheduled)?.exact ?: false}"
        if (delivery is ScheduleResult.Failed || timeout is ScheduleResult.Failed) {
            repository.updateSchedulingHandle(occurrence.occurrenceId, null)
            return if (delivery is ScheduleResult.Failed) delivery else timeout
        }
        // A prescription can retire while AlarmManager is being called. Retired slots never retain a new handle.
        val current = repository.getOccurrence(occurrence.occurrenceId)
        if (current == null || current.state !in pendingStates || !repository.isOccurrenceCurrent(current)) {
            cancelOccurrenceHandles(occurrence.occurrenceId)
            repository.updateSchedulingHandle(occurrence.occurrenceId, null)
            return ScheduleResult.Unavailable("retired_during_schedule")
        }
        repository.updateSchedulingHandle(occurrence.occurrenceId, description)
        return if (delivery is ScheduleResult.Scheduled) delivery else timeout
    }

    suspend fun scheduleTimeout(request: MedicationLog): ScheduleResult = scheduling.withLock {
        val occurrence = repository.getOccurrence(request.occurrenceId) ?: return@withLock ScheduleResult.Unavailable("deleted")
        if (occurrence.state !in pendingStates || !repository.isOccurrenceCurrent(occurrence)) return@withLock ScheduleResult.Unavailable("stale")
        val result = scheduleHandle(occurrence.occurrenceId, AlarmIdentity.TIMEOUT, occurrence.deadlineAt, MarkMissedReceiver::class.java)
        val current = repository.getOccurrence(occurrence.occurrenceId)
        if (current == null || current.state !in pendingStates || !repository.isOccurrenceCurrent(current)) {
            cancelOccurrenceHandles(occurrence.occurrenceId)
            return@withLock ScheduleResult.Unavailable("retired_during_schedule")
        }
        result
    }

    suspend fun scheduleFollowUp(occurrence: MedicationLog): ScheduleResult = scheduling.withLock { scheduleFollowUpLocked(occurrence) }
    private suspend fun scheduleFollowUpLocked(request: MedicationLog): ScheduleResult {
        val occurrence = repository.getOccurrence(request.occurrenceId) ?: return ScheduleResult.Unavailable("deleted")
        if (!repository.isOccurrenceCurrent(occurrence)) return ScheduleResult.Unavailable("stale")
        val at = occurrence.reminderAt ?: return ScheduleResult.Unavailable("no_follow_up")
        if (occurrence.state != DoseState.MISSED) return ScheduleResult.Unavailable("closed")
        val result = scheduleHandle(occurrence.occurrenceId, AlarmIdentity.FOLLOW_UP, maxOf(at, System.currentTimeMillis() + 1000), MissedReminderReceiver::class.java)
        val current = repository.getOccurrence(occurrence.occurrenceId)
        if (current == null || current.state != DoseState.MISSED || current.reminderAt == null || !repository.isOccurrenceCurrent(current)) {
            cancelOccurrenceHandles(occurrence.occurrenceId)
            return ScheduleResult.Unavailable("retired_during_schedule")
        }
        return result
    }

    suspend fun snoozeOccurrence(id: String, minutes: Int): DoseActionResult = scheduling.withLock {
        val until = System.currentTimeMillis() + minutes.coerceIn(1, 60) * 60_000L
        val result = DoseActionCoordinator(repository).snooze(id, until)
        if (result is DoseActionResult.Success) {
            cancelOccurrenceHandles(id)
            val scheduled = scheduleOccurrenceLocked(result.occurrence)
            if (scheduled !is ScheduleResult.Scheduled) return@withLock DoseActionResult.Failure(IllegalStateException("snooze_not_scheduled"))
            AlarmService.stopAlarm(context, id)
        }
        result
    }

    suspend fun cancelOccurrence(id: String) = scheduling.withLock {
        cancelOccurrenceHandles(id)
        repository.updateSchedulingHandle(id, null)
        AlarmService.stopAlarm(context, id)
        com.dosecerta.notification.NotificationHelper(context).cancel(id)
    }

    suspend fun cancelAlarm(medicationId: Long, scheduleId: Long) {
        cancelLegacy(medicationId, scheduleId)
        repository.getAllLogs().first().filter { it.scheduleId == scheduleId }
            .forEach { cancelOccurrence(it.occurrenceId) }
    }
    suspend fun cancelAlarmsForMedication(medicationId: Long, schedules: List<Schedule>) {
        schedules.forEach { cancelAlarm(medicationId, it.id) }
    }

    suspend fun cancelMissedReminderAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long) {
        repository.getLog(medicationId, scheduleId, scheduledTime)?.let { cancelOccurrence(it.occurrenceId) }
    }
    suspend fun cancelMissedCheckAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long) =
        cancelMissedReminderAlarm(medicationId, scheduleId, scheduledTime)

    /** Compatibility entry points resolve a stored occurrence, never manufacture a new dose from stale extras. */
    suspend fun snoozeAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long, minutes: Int = Constants.SNOOZE_DURATION_MINUTES) {
        repository.getLog(medicationId, scheduleId, scheduledTime)?.let { snoozeOccurrence(it.occurrenceId, minutes) }
    }
    suspend fun scheduleMissedCheckAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long) {
        repository.getLog(medicationId, scheduleId, scheduledTime)?.let { scheduleTimeout(it) }
    }
    suspend fun scheduleMissedReminderAlarm(medicationId: Long, scheduleId: Long, scheduledTime: Long, hours: Int = Constants.MISSED_REMINDER_DELAY_HOURS) {
        repository.getLog(medicationId, scheduleId, scheduledTime)?.let { scheduleFollowUp(it) }
    }

    suspend fun reconcile() {
        AlarmDiagnostics.record(context, "reconcile", result = "begin")
        repository.reconcileBacklog()
        repository.getAllLogs().first().filter { it.schedulerHandle != null && (it.state !in pendingStates || !repository.isOccurrenceCurrent(it)) }.forEach { cancelOccurrence(it.occurrenceId) }
        repository.getPendingOccurrences().forEach { scheduleOccurrence(it) }
        repository.getFollowUpOccurrences().forEach {
            if (repository.isOccurrenceCurrent(it)) scheduleFollowUp(it) else cancelOccurrence(it.occurrenceId)
        }
        repository.getAllActiveSchedulesSync().forEach { scheduleAlarm(it.medicationId, it) }
        AlarmDiagnostics.record(context, "reconcile", result = "done")
    }

    private fun scheduleHandle(id: String, kind: String, at: Long, target: Class<*>): ScheduleResult {
        val exactAllowed = ReminderCapabilityChecker(context).check().exactAlarms
        val intent = AlarmIdentity.intent(context, target, id, kind).putExtra(AlarmIdentity.EXTRA_EXACT, exactAllowed)
        var pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            if (exactAllowed) {
                try {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    AlarmDiagnostics.record(context, "schedule_$kind", id, "exact")
                    return ScheduleResult.Scheduled(id, at, true)
                } catch (_: SecurityException) {
                    // Access can be revoked between the capability check and AlarmManager call.
                    intent.putExtra(AlarmIdentity.EXTRA_EXACT, false)
                    pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                }
            }
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            AlarmDiagnostics.record(context, "schedule_$kind", id, "inexact")
            return ScheduleResult.Scheduled(id, at, false)
        } catch (error: RuntimeException) {
            AlarmDiagnostics.record(context, "schedule_$kind", id, error.javaClass.simpleName)
            return ScheduleResult.Failed(error)
        }
    }

    private fun cancelOccurrenceHandles(id: String) {
        listOf(AlarmIdentity.RECURRENCE to MedicationAlarmReceiver::class.java, AlarmIdentity.SNOOZE to MedicationAlarmReceiver::class.java,
            AlarmIdentity.TIMEOUT to MarkMissedReceiver::class.java, AlarmIdentity.FOLLOW_UP to MissedReminderReceiver::class.java).forEach { (kind, target) ->
            PendingIntent.getBroadcast(context, 0, AlarmIdentity.intent(context, target, id, kind), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                manager.cancel(it); it.cancel()
            }
        }
    }

    private fun cancelLegacy(medicationId: Long, scheduleId: Long) {
        listOf(Triple(MedicationAlarmReceiver::class.java, (medicationId * 1000 + scheduleId).toInt(), null),
            Triple(MarkMissedReceiver::class.java, (medicationId * 1000000 + scheduleId * 1000 + 999).toInt(), Constants.ACTION_MARK_MISSED),
            Triple(MissedReminderReceiver::class.java, (medicationId * 1000000 + scheduleId * 1000 + 998).toInt(), null)).forEach { (target, code, action) ->
            val intent = Intent(context, target).setAction(action)
            PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { manager.cancel(it); it.cancel() }
        }
    }

    private val pendingStates = setOf(DoseState.PENDING, DoseState.ALERTING, DoseState.SNOOZED, DoseState.DISMISSED)
}
