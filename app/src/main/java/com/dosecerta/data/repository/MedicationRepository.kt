package com.dosecerta.data.repository

import androidx.room.withTransaction
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.dao.MedicationDao
import com.dosecerta.data.local.dao.MedicationLogDao
import com.dosecerta.data.local.dao.MedicationLogWithDetails
import com.dosecerta.data.local.dao.ScheduleDao
import com.dosecerta.data.local.entity.*
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime

/** Every multi-row command commits atomically before consumers schedule or acknowledge it. */
class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val medicationLogDao: MedicationLogDao,
    private val database: DoseCertaDatabase? = DoseCertaDatabase.initializedInstance(),
    private val clock: Clock = Clock.systemUTC()
) {
    constructor(database: DoseCertaDatabase, clock: Clock = Clock.systemUTC()) : this(
        database.medicationDao(), database.scheduleDao(), database.medicationLogDao(), database, clock
    )

    private suspend fun <T> transaction(block: suspend () -> T): T =
        requireNotNull(database) { "A Room database is required for atomic medication commands" }.withTransaction(block)

    fun getAllActiveMedications(): Flow<List<Medication>> = medicationDao.getAllActiveMedications()
    suspend fun getAllActiveMedicationsSync(): List<Medication> = medicationDao.getAllActiveMedicationsSync()
    fun getMedicationById(id: Long): Flow<Medication?> = medicationDao.getMedicationById(id)
    suspend fun getMedicationByIdSync(id: Long): Medication? = medicationDao.getMedicationByIdSync(id)
    fun searchMedications(query: String): Flow<List<Medication>> = medicationDao.searchMedications(query)
    fun getActiveMedicationCount(): Flow<Int> = medicationDao.getActiveMedicationCount()
    suspend fun insertMedication(medication: Medication): Long = medicationDao.insert(medication)
    suspend fun updateMedication(medication: Medication) = transaction {
        val old = requireNotNull(medicationDao.getMedicationByIdSync(medication.id))
        medicationDao.update(medication.copy(createdAt = old.createdAt))
    }

    suspend fun saveMedicationWithSchedules(medication: Medication, schedules: List<Schedule>, requestId: String? = null): SavedMedication = transaction {
        requestId?.let { request -> medicationDao.getSavedMedicationId(request)?.let { savedId ->
            requireNotNull(medicationDao.getMedicationByIdSync(savedId)) { "The saved medication was permanently removed; reopen the form" }
            return@transaction SavedMedication(savedId, scheduleDao.getSchedulesForMedicationSync(savedId),
                scheduleDao.getScheduleVersionsForMedication(savedId).filter { !it.isActive }.map { it.id })
        } }
        require(medication.name.isNotBlank() && medication.dosage.isNotBlank() && medication.unit.isNotBlank())
        schedules.forEach(::validateSchedule)
        require(schedules.map { Triple(it.timeInMinutes, it.recurrenceKind, Pair(it.daysOfWeek.sorted(), it.monthDay)) }.distinct().size == schedules.size) {
            "Duplicate reminder slots are not allowed"
        }
        require(medication.frequency == Frequency.AS_NEEDED || schedules.isNotEmpty()) { "Select at least one reminder time" }
        val now = clock.millis()
        val existing = if (medication.id != 0L) requireNotNull(medicationDao.getMedicationByIdSync(medication.id)) else null
        val id = existing?.id ?: medicationDao.insert(medication.copy(id = 0, createdAt = now))
        val oldSchedules = scheduleDao.getSchedulesForMedicationSync(id)
        if (existing != null) {
            reconcileBacklogInternal(now)
            medicationDao.update(medication.copy(id = id, createdAt = existing.createdAt))
        }
        val version = (scheduleDao.getScheduleVersionsForMedication(id).maxOfOrNull { it.version } ?: 0L) + 1L
        for (old in oldSchedules) {
            scheduleDao.update(old.copy(isActive = false, validUntil = now))
            medicationLogDao.cancelPendingForSchedule(old.id)
        }
        val active = if (medication.frequency == Frequency.AS_NEEDED) emptyList() else schedules.map { slot ->
            val candidate = slot.copy(id = 0, medicationId = id, isActive = true, version = version, validFrom = now, validUntil = null)
            candidate.copy(id = scheduleDao.insert(candidate))
        }
        requestId?.let { medicationDao.insertSaveReceipt(MedicationSaveReceipt(it, id)) }
        SavedMedication(id, active, oldSchedules.map { it.id })
    }

    /** Soft removal preserves all history and retires only this medication's active slots. */
    suspend fun archiveMedication(id: Long): List<Long> = transaction {
        reconcileBacklogInternal(clock.millis())
        val active = scheduleDao.getSchedulesForMedicationSync(id)
        active.forEach { slot ->
            scheduleDao.update(slot.copy(isActive = false, validUntil = clock.millis()))
            medicationLogDao.cancelPendingForSchedule(slot.id)
        }
        medicationDao.deactivate(id)
        active.map { it.id }
    }

    suspend fun permanentlyDeleteMedication(id: Long, deleteHistory: Boolean): List<Long> = transaction {
        val slots = scheduleDao.getScheduleVersionsForMedication(id)
        slots.forEach { medicationLogDao.cancelPendingForSchedule(it.id) }
        if (deleteHistory) {
            medicationLogDao.getLogsForMedicationSync(id).forEach {
                medicationLogDao.insertSuppression(OccurrenceSuppression(it.occurrenceId, clock.millis()))
            }
            medicationLogDao.deleteAllForMedication(id)
        }
        // SET_NULL retains frozen history when the user elects to keep it.
        medicationDao.deleteById(id)
        slots.map { it.id }
    }

    /** Compatibility: the default list action archives, permanent removal is always explicit. */
    suspend fun deleteMedication(medication: Medication) { archiveMedication(medication.id) }
    suspend fun deleteMedicationById(id: Long) { archiveMedication(id) }
    fun getSchedulesForMedication(medicationId: Long): Flow<List<Schedule>> = scheduleDao.getSchedulesForMedication(medicationId)
    suspend fun getSchedulesForMedicationSync(medicationId: Long): List<Schedule> = scheduleDao.getSchedulesForMedicationSync(medicationId)
    suspend fun getScheduleById(id: Long): Schedule? = scheduleDao.getScheduleById(id)
    fun getAllActiveSchedules(): Flow<List<Schedule>> = scheduleDao.getAllActiveSchedules()
    suspend fun getAllActiveSchedulesSync(): List<Schedule> = scheduleDao.getAllActiveSchedulesSync()
    suspend fun insertSchedule(schedule: Schedule): Long { validateSchedule(schedule); return scheduleDao.insert(schedule) }
    suspend fun insertSchedules(schedules: List<Schedule>) = transaction {
        schedules.forEach(::validateSchedule)
        require(schedules.map { Pair(it.medicationId, it.timeInMinutes) }.distinct().size == schedules.size)
        scheduleDao.insertAll(schedules)
    }
    suspend fun updateSchedule(schedule: Schedule) = transaction {
        validateSchedule(schedule)
        val old = requireNotNull(scheduleDao.getScheduleById(schedule.id))
        scheduleDao.update(old.copy(isActive = false, validUntil = clock.millis()))
        medicationLogDao.cancelPendingForSchedule(old.id)
        scheduleDao.insert(schedule.copy(id = 0, version = old.version + 1, validFrom = clock.millis(), validUntil = null))
    }
    suspend fun deleteSchedule(schedule: Schedule) = transaction {
        scheduleDao.update(schedule.copy(isActive = false, validUntil = clock.millis()))
        medicationLogDao.cancelPendingForSchedule(schedule.id)
    }
    suspend fun deleteAllSchedulesForMedication(medicationId: Long) = transaction {
        scheduleDao.getSchedulesForMedicationSync(medicationId).forEach {
            scheduleDao.update(it.copy(isActive = false, validUntil = clock.millis()))
            medicationLogDao.cancelPendingForSchedule(it.id)
        }
    }

    fun getAllLogs(): Flow<List<MedicationLog>> = medicationLogDao.getAllLogs()
    fun getLogsForMedication(medicationId: Long): Flow<List<MedicationLog>> = medicationLogDao.getLogsForMedication(medicationId)
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<MedicationLog>> = medicationLogDao.getLogsInRange(startTime, endTime)
    fun getLogsByStatusInRange(startTime: Long, endTime: Long, status: MedicationStatus): Flow<List<MedicationLog>> = medicationLogDao.getLogsByStatusInRange(startTime, endTime, status)
    fun getAllLogsWithDetails(): Flow<List<MedicationLogWithDetails>> = medicationLogDao.getAllLogsWithDetails()
    fun getAllLogsByStatusWithDetails(status: MedicationStatus): Flow<List<MedicationLogWithDetails>> = medicationLogDao.getAllLogsByStatusWithDetails(status)
    fun getLogsInRangeWithDetails(startTime: Long, endTime: Long): Flow<List<MedicationLogWithDetails>> = medicationLogDao.getLogsInRangeWithDetails(startTime, endTime)
    fun getLogsByStatusInRangeWithDetails(startTime: Long, endTime: Long, status: MedicationStatus): Flow<List<MedicationLogWithDetails>> = medicationLogDao.getLogsByStatusInRangeWithDetails(startTime, endTime, status)
    suspend fun reportSnapshot(startInclusive: Long, endExclusive: Long): List<MedicationLogWithDetails> = transaction {
        require(endExclusive >= startInclusive)
        medicationLogDao.reportSnapshot(startInclusive, endExclusive).toList()
    }
    fun getCountByStatus(status: MedicationStatus, startTime: Long): Flow<Int> = medicationLogDao.getCountByStatus(status, startTime)
    suspend fun getTakenCountInRange(startTime: Long, endTime: Long): Int = medicationLogDao.getTakenCountInRange(startTime, endTime)
    suspend fun getMissedCountInRange(startTime: Long, endTime: Long): Int = medicationLogDao.getCountByStatusInRange(MedicationStatus.MISSED, startTime, endTime)
    suspend fun getSkippedCountInRange(startTime: Long, endTime: Long): Int = medicationLogDao.getCountByStatusInRange(MedicationStatus.SKIPPED, startTime, endTime)
    suspend fun getTotalCountInRange(startTime: Long, endTime: Long): Int = medicationLogDao.getTotalCountInRange(startTime, endTime)
    suspend fun getLog(medicationId: Long, scheduleId: Long, scheduledTime: Long): MedicationLog? = medicationLogDao.getLog(medicationId, scheduleId, scheduledTime)
    suspend fun getOccurrence(occurrenceId: String): MedicationLog? = medicationLogDao.getOccurrence(occurrenceId)
    suspend fun getPendingOccurrences(): List<MedicationLog> = medicationLogDao.getPendingOccurrences()
    suspend fun getFollowUpOccurrences(): List<MedicationLog> = medicationLogDao.getFollowUpOccurrences()
    suspend fun updateSchedulingHandle(occurrenceId: String, handle: String?) = medicationLogDao.updateSchedulingHandle(occurrenceId, handle)
    suspend fun clearReminder(occurrenceId: String) = medicationLogDao.clearReminder(occurrenceId)
    suspend fun isOccurrenceSuppressed(schedule: Schedule, localSlot: LocalDateTime): Boolean =
        medicationLogDao.suppressionCount(occurrenceKey(schedule, localSlot)) > 0

    suspend fun ensureOccurrence(scheduleId: Long, originalDueAt: Long): MedicationLog? = transaction {
        ensureOccurrenceInternal(scheduleId, originalDueAt)
    }

    private suspend fun ensureOccurrenceInternal(scheduleId: Long, due: Long): MedicationLog? {
        val schedule = scheduleDao.getScheduleById(scheduleId) ?: return null
        val medication = medicationDao.getMedicationByIdSync(schedule.medicationId) ?: return null
        if (!schedule.isActive || !medication.isActive || medication.frequency == Frequency.AS_NEEDED) return null
        // A captured instant remains addressable even when the device zone has since changed.
        medicationLogDao.getLog(medication.id, schedule.id, due)?.let { existing ->
            if (existing.scheduleVersion == schedule.version) return existing
        }
        val slot = RecurrenceCalculator(clock).occurrencesBetween(schedule, Instant.ofEpochMilli(due), Instant.ofEpochMilli(due + 1)).singleOrNull() ?: return null
        val occurrenceId = occurrenceKey(schedule, slot.requestedLocalDateTime)
        if (medicationLogDao.suppressionCount(occurrenceId) > 0) return null
        medicationLogDao.getOccurrence(occurrenceId)?.let { return it }
        val log = capturedLog(MedicationLog(medicationId = medication.id, scheduleId = schedule.id,
            scheduledTime = slot.originalDueAt, status = MedicationStatus.PENDING, scheduleVersion = schedule.version,
            originalLocalDateTime = slot.requestedLocalDateTime.toString(), originalZoneId = slot.zoneId,
            occurrenceId = occurrenceId), medication)
        val id = medicationLogDao.insert(log)
        return if (id == -1L) medicationLogDao.getOccurrence(occurrenceId) else log.copy(id = id)
    }

    suspend fun isOccurrenceCurrent(log: MedicationLog): Boolean {
        if (log.state == DoseState.CANCELLED || log.scheduleId == null || log.medicationId == null) return false
        val schedule = scheduleDao.getScheduleById(log.scheduleId) ?: return false
        val medication = medicationDao.getMedicationByIdSync(log.medicationId) ?: return false
        return schedule.isActive && medication.isActive && medication.frequency != Frequency.AS_NEEDED && schedule.version == log.scheduleVersion
    }

    /** Captured slots retain their original timestamp; the preview labels these after a zone change. */
    suspend fun previewOccurrences(schedule: Schedule, count: Int = 5, after: Instant = clock.instant()): List<OccurrenceDate> {
        require(count in 1..100)
        val retained = medicationLogDao.getPendingOccurrences().filter {
            it.scheduleId == schedule.id && it.scheduleVersion == schedule.version && it.originalDueAt > after.toEpochMilli()
        }.map { captured ->
            val requested = LocalDateTime.parse(captured.originalLocalDateTime)
            val originalZone = ZoneId.of(captured.originalZoneId)
            OccurrenceDate(captured.originalDueAt, Instant.ofEpochMilli(captured.originalDueAt).atZone(originalZone).toLocalDateTime(),
                captured.originalZoneId, originalZone.rules.getValidOffsets(requested).size != 1, requested, true)
        }
        val calculated = mutableListOf<OccurrenceDate>()
        val calculator = RecurrenceCalculator(clock)
        var cursor = after
        while (calculated.size < count) {
            val candidate = calculator.nextOccurrence(schedule, cursor) ?: break
            cursor = Instant.ofEpochMilli(candidate.originalDueAt)
            val key = occurrenceKey(schedule, candidate.requestedLocalDateTime)
            if (medicationLogDao.getOccurrence(key) == null && medicationLogDao.suppressionCount(key) == 0) calculated += candidate
        }
        return (retained + calculated).distinctBy { it.requestedLocalDateTime }.sortedBy { it.originalDueAt }.take(count)
    }

    suspend fun command(occurrenceId: String, command: DoseCommand, until: Long? = null,
        reminderDelayMillis: Long = DosePolicy.REMINDER_DELAY_MILLIS): DoseActionResult = guarded {
        transaction {
            val old = medicationLogDao.getOccurrence(occurrenceId) ?: return@transaction DoseActionResult.Rejected("Occurrence not found")
            if (!isOccurrenceCurrent(old)) return@transaction DoseActionResult.Rejected("The prescription or occurrence is no longer active")
            val now = clock.millis()
            val transition = DoseStateMachine.transition(old.state, command, old.originalDueAt, now, old.deadlineAt, old.snoozedUntil, until)
                ?: return@transaction DoseActionResult.Rejected("This action is expired or incompatible with the recorded outcome")
            if (!transition.changed) return@transaction DoseActionResult.Success(old, false)
            val status = when (transition.state) {
                DoseState.TAKEN -> MedicationStatus.TAKEN
                DoseState.SKIPPED -> MedicationStatus.SKIPPED
                DoseState.MISSED -> MedicationStatus.MISSED
                else -> MedicationStatus.PENDING
            }
            val updated = old.copy(state = transition.state, status = status, deadlineAt = transition.deadlineAt,
                snoozedUntil = transition.snoozedUntil,
                actualTime = if (transition.state == DoseState.TAKEN) now else null,
                deliveredAt = if (command == DoseCommand.DELIVER) now else old.deliveredAt,
                reminderAt = if (transition.state == DoseState.MISSED) now + reminderDelayMillis.coerceAtLeast(0) else null)
            medicationLogDao.update(updated)
            DoseActionResult.Success(updated, true)
        }
    }

    /** Corrections intentionally differ from live alarm commands; TAKEN always supplies an explicit time. */
    suspend fun correctLog(logId: Long, newStatus: MedicationStatus, takenAt: Long?): DoseActionResult = guarded {
        transaction {
            val old = medicationLogDao.getById(logId) ?: return@transaction DoseActionResult.Rejected("Record not found")
            if (old.state == DoseState.CANCELLED || newStatus == MedicationStatus.PENDING) return@transaction DoseActionResult.Rejected("Select a completed outcome")
            if (newStatus == MedicationStatus.TAKEN && takenAt == null) return@transaction DoseActionResult.Rejected("Choose the intake time")
            val corrected = old.copy(status = newStatus, state = DoseState.valueOf(newStatus.name), actualTime = if (newStatus == MedicationStatus.TAKEN) takenAt else null,
                snoozedUntil = null, reminderAt = null, schedulerHandle = null)
            if (corrected == old) return@transaction DoseActionResult.Success(old, false)
            medicationLogDao.update(corrected)
            DoseActionResult.Success(corrected, true)
        }
    }

    /** Legacy callers retain identity/snapshot; no edit can rewrite the original scheduled timestamp. */
    suspend fun updateLog(log: MedicationLog) {
        when (val result = correctLog(log.id, log.status, log.actualTime)) {
            is DoseActionResult.Success -> Unit
            is DoseActionResult.Rejected -> error(result.reason)
            is DoseActionResult.Failure -> throw result.error
        }
    }
    suspend fun insertLog(log: MedicationLog): Long = transaction {
        require(log.actualTime == null || log.status == MedicationStatus.TAKEN)
        require(medicationLogDao.suppressionCount(log.occurrenceId) == 0) { "This occurrence was deleted" }
        val medication = log.medicationId?.let { medicationDao.getMedicationByIdSync(it) }
        val captured = capturedLog(log, medication)
        val id = medicationLogDao.insert(captured)
        if (id != -1L) id else requireNotNull(medicationLogDao.getOccurrence(log.occurrenceId)).id
    }
    suspend fun deleteLog(log: MedicationLog) = transaction {
        medicationLogDao.insertSuppression(OccurrenceSuppression(log.occurrenceId, clock.millis()))
        medicationLogDao.delete(log)
    }

    suspend fun recordExtraDose(medicationId: Long, requestId: String? = null): Long = transaction {
        val token = requestId?.let { "extra-request:$it" }
        token?.let { medicationLogDao.getOccurrence(it)?.let { found -> return@transaction found.id } }
        require(token == null || medicationLogDao.suppressionCount(token) == 0) { "This intake request was deleted" }
        val medication = requireNotNull(medicationDao.getMedicationByIdSync(medicationId))
        require(medication.isActive)
        val now = clock.millis()
        val entry = capturedLog(MedicationLog(medicationId = medicationId, scheduleId = null, scheduledTime = now,
            actualTime = now, status = MedicationStatus.TAKEN, isExtraDose = true), medication)
        medicationLogDao.insert(if (token == null) entry else entry.copy(occurrenceId = token))
    }
    suspend fun recordCustomExtraDose(medicationName: String, requestId: String? = null): Long = transaction {
        val token = requestId?.let { "extra-request:$it" }
        token?.let { medicationLogDao.getOccurrence(it)?.let { found -> return@transaction found.id } }
        require(token == null || medicationLogDao.suppressionCount(token) == 0) { "This intake request was deleted" }
        require(medicationName.isNotBlank())
        val now = clock.millis()
        val entry = MedicationLog(medicationId = null, scheduleId = null, scheduledTime = now,
            actualTime = now, status = MedicationStatus.TAKEN, isExtraDose = true, customMedicationName = medicationName.trim(), snapshotName = medicationName.trim())
        medicationLogDao.insert(if (token == null) entry else entry.copy(occurrenceId = token))
    }
    suspend fun calculateAdherence(startTime: Long, endTime: Long): Int? =
        AdherenceCalculator.calculate(reportSnapshot(startTime, endTime + 1).map { it.log }).percentage

    /** Restore the entire known interval; missing offline doses are recorded without audio/follow-up bursts. */
    suspend fun reconcileBacklog(now: Long = clock.millis()) = transaction { reconcileBacklogInternal(now) }
    private suspend fun reconcileBacklogInternal(now: Long) {
        val checkpoint = medicationLogDao.getReconciliationCheckpoint()
        for (schedule in scheduleDao.getAllActiveSchedulesSync()) {
            val medication = medicationDao.getMedicationByIdSync(schedule.medicationId) ?: continue
            if (!medication.isActive || medication.frequency == Frequency.AS_NEEDED) continue
            // A checkpoint from before a backwards clock correction cannot prove that a
            // newly created prescription already existed. Revisit the known version window;
            // stable local-slot identities and deletion tombstones make this idempotent.
            val from = if (checkpoint != null && checkpoint > now) schedule.validFrom
                else maxOf(schedule.validFrom, checkpoint ?: schedule.validFrom)
            if (from >= now) continue
            for (slot in RecurrenceCalculator(clock).occurrencesBetween(schedule, Instant.ofEpochMilli(from), Instant.ofEpochMilli(now))) {
                val occurrence = ensureOccurrenceInternal(schedule.id, slot.originalDueAt) ?: continue
                if (occurrence.state in pendingStates && now >= occurrence.deadlineAt) {
                    medicationLogDao.update(occurrence.copy(state = DoseState.MISSED, status = MedicationStatus.MISSED, actualTime = null, reminderAt = null, schedulerHandle = null))
                }
            }
        }
        for (pending in medicationLogDao.getPendingOccurrences()) {
            if (!isOccurrenceCurrent(pending)) medicationLogDao.update(pending.copy(state = DoseState.CANCELLED, reminderAt = null, schedulerHandle = null))
            else if (now >= pending.deadlineAt) medicationLogDao.update(pending.copy(state = DoseState.MISSED, status = MedicationStatus.MISSED, actualTime = null, reminderAt = null, schedulerHandle = null))
        }
        // A backwards wall-clock correction cannot move the already reconciled boundary backwards.
        medicationLogDao.setReconciliationCheckpoint(ReconciliationCheckpoint(reconciledUntil = maxOf(now, checkpoint ?: now)))
    }

    private fun capturedLog(log: MedicationLog, medication: Medication?): MedicationLog = log.copy(
        snapshotName = log.customMedicationName ?: medication?.name ?: log.snapshotName,
        snapshotDosage = medication?.dosage ?: log.snapshotDosage, snapshotUnit = medication?.unit ?: log.snapshotUnit,
        snapshotColor = medication?.color ?: log.snapshotColor, snapshotForm = medication?.pharmaceuticalForm ?: log.snapshotForm,
        snapshotFrequency = medication?.frequency ?: log.snapshotFrequency, snapshotNotes = medication?.notes ?: log.snapshotNotes,
        isScheduledDose = log.isScheduledDose && medication?.frequency != Frequency.AS_NEEDED
    )
    private fun validateSchedule(schedule: Schedule) {
        require(schedule.timeInMinutes in 0..1439)
        require(schedule.daysOfWeek.distinct().size == schedule.daysOfWeek.size && schedule.daysOfWeek.all { it in 1..7 })
        require(schedule.recurrenceKind !in setOf(RecurrenceKind.WEEKLY, RecurrenceKind.SELECTED_DAYS) || schedule.daysOfWeek.isNotEmpty())
        require(schedule.recurrenceKind != RecurrenceKind.MONTHLY || schedule.monthDay in 1..31)
        if (schedule.zoneId.isNotBlank()) ZoneId.of(schedule.zoneId)
    }
    private suspend fun guarded(block: suspend () -> DoseActionResult): DoseActionResult = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { DoseActionResult.Failure(error) }

    companion object {
        fun occurrenceKey(schedule: Schedule, localSlot: LocalDateTime): String = "dose:${schedule.id}:${schedule.version}:$localSlot"
        private val pendingStates = setOf(DoseState.PENDING, DoseState.ALERTING, DoseState.SNOOZED, DoseState.DISMISSED)
    }
}

data class SavedMedication(val id: Long, val activeSchedules: List<Schedule>, val retiredScheduleIds: List<Long>)
