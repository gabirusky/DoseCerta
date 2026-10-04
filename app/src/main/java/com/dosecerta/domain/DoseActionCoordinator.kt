package com.dosecerta.domain

import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.repository.MedicationRepository

sealed interface DoseActionResult {
    data class Success(val occurrence: MedicationLog, val changed: Boolean) : DoseActionResult
    data class Rejected(val reason: String) : DoseActionResult
    data class Failure(val error: Throwable) : DoseActionResult
}

/** One persistent command path for Home, card, notifications and receivers. */
class DoseActionCoordinator(private val repository: MedicationRepository) {
    suspend fun deliver(occurrenceId: String) = repository.command(occurrenceId, DoseCommand.DELIVER)
    suspend fun take(occurrenceId: String) = repository.command(occurrenceId, DoseCommand.TAKE)
    suspend fun skip(occurrenceId: String) = repository.command(occurrenceId, DoseCommand.SKIP)
    suspend fun dismiss(occurrenceId: String) = repository.command(occurrenceId, DoseCommand.DISMISS)
    suspend fun timeout(occurrenceId: String, reminderDelayMillis: Long = DosePolicy.REMINDER_DELAY_MILLIS) =
        repository.command(occurrenceId, DoseCommand.TIMEOUT, reminderDelayMillis = reminderDelayMillis)
    suspend fun cancel(occurrenceId: String) = repository.command(occurrenceId, DoseCommand.CANCEL)
    suspend fun snooze(occurrenceId: String, until: Long) = repository.command(occurrenceId, DoseCommand.SNOOZE, until)
    suspend fun correct(logId: Long, status: MedicationStatus, takenAt: Long?) = repository.correctLog(logId, status, takenAt)
}
