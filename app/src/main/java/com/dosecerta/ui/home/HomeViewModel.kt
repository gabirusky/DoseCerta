package com.dosecerta.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.ScheduleItem
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.AdherenceCalculator
import com.dosecerta.domain.AdherenceSummary
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import com.dosecerta.domain.DoseState
import com.dosecerta.domain.RecurrenceCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

class HomeViewModel(private val repository: MedicationRepository, private val alarmScheduler: AlarmScheduler,
    private val savedState: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()) : ViewModel() {
    private val calculator = RecurrenceCalculator()
    private val coordinator = DoseActionCoordinator(repository)
    /** Time changes redraw dates and relative labels without creating dose records. */
    val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())
    val activeMedications = repository.getAllActiveMedications()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val asNeededMedications = activeMedications.map { meds -> meds.filter { it.frequency == Frequency.AS_NEEDED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todaySchedule = combine(repository.getAllActiveSchedules(), repository.getAllLogs(), activeMedications, clock) { schedules, logs, medications, now ->
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val start = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val medicationsById = medications.associateBy { it.id }
        val logsBySchedule = logs.groupBy { it.scheduleId to it.scheduleVersion }
        schedules.flatMap { schedule ->
            val med = medicationsById[schedule.medicationId]?.takeIf { it.frequency != Frequency.AS_NEEDED } ?: return@flatMap emptyList()
            val captured = logsBySchedule[schedule.id to schedule.version].orEmpty()
            val todayCaptured = captured.filter { it.state != DoseState.CANCELLED && it.originalDueAt >= start.toEpochMilli() && it.originalDueAt < end.toEpochMilli() }
            val items = todayCaptured.map { log -> ScheduleItem(med, schedule, log.originalDueAt, log.status,
                log.originalDueAt < now && log.status == MedicationStatus.PENDING) }.toMutableList()
            calculator.occurrencesBetween(schedule, start, end).forEach { due ->
                // One local slot has one identity even if a timezone change moves the calculated instant.
                val log = captured.find { it.originalLocalDateTime == due.requestedLocalDateTime.toString() }
                if (log == null && !repository.isOccurrenceSuppressed(schedule, due.requestedLocalDateTime)) {
                    items += ScheduleItem(med, schedule, due.originalDueAt, MedicationStatus.PENDING, due.originalDueAt < now)
                }
            }
            items
        }.sortedBy { it.scheduledTime }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val upcoming = combine(repository.getAllActiveSchedules(), activeMedications, clock) { schedules, medications, now ->
        val medicationsById = medications.associateBy { it.id }
        schedules.mapNotNull { schedule ->
            val med = medicationsById[schedule.medicationId]?.takeIf { it.frequency != Frequency.AS_NEEDED } ?: return@mapNotNull null
            repository.previewOccurrences(schedule, 1, Instant.ofEpochMilli(now)).firstOrNull()?.let { med.name to it.originalDueAt }
        }.sortedBy { it.second }.take(5)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val statistics = combine(repository.getAllLogs(), clock) { logs, now ->
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = today.minusDays((today.dayOfWeek.value - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        AdherenceCalculator.calculate(logs.filter { it.originalDueAt in start..now })
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdherenceSummary(0, 0, 0, 0))
    private val actionMutex = Mutex()
    suspend fun markAsTaken(item: ScheduleItem): Boolean = actionMutex.withLock {
        val occurrence = repository.getLog(item.medication.id, item.schedule.id, item.scheduledTime)
            ?: repository.ensureOccurrence(item.schedule.id, item.scheduledTime) ?: return@withLock false
        val result = coordinator.take(occurrence.occurrenceId)
        if (result is DoseActionResult.Success) { alarmScheduler.cancelOccurrence(occurrence.occurrenceId); true } else false
    }
    suspend fun skipMedication(item: ScheduleItem): Boolean = actionMutex.withLock {
        val occurrence = repository.getLog(item.medication.id, item.schedule.id, item.scheduledTime)
            ?: repository.ensureOccurrence(item.schedule.id, item.scheduledTime) ?: return@withLock false
        val result = coordinator.skip(occurrence.occurrenceId)
        if (result is DoseActionResult.Success) { alarmScheduler.cancelOccurrence(occurrence.occurrenceId); true } else false
    }
    private val extraMutex = Mutex()
    private fun request(key: String): String {
        val pendingKey = savedState.get<String>("extra_key")
        if (pendingKey != key) { savedState["extra_key"] = key; savedState["extra_request"] = java.util.UUID.randomUUID().toString() }
        return savedState.get<String>("extra_request") ?: java.util.UUID.randomUUID().toString().also { savedState["extra_request"] = it }
    }
    suspend fun recordExtraDose(medicationId: Long) = extraMutex.withLock {
        repository.recordExtraDose(medicationId, request("id:$medicationId"))
        savedState["extra_key"] = null; savedState["extra_request"] = null
    }
    suspend fun recordCustomExtraDose(name: String) = extraMutex.withLock {
        require(name.isNotBlank()); repository.recordCustomExtraDose(name.trim(), request("name:${name.trim()}"))
        savedState["extra_key"] = null; savedState["extra_request"] = null
    }
}
