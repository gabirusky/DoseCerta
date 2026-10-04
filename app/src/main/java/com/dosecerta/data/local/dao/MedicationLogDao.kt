package com.dosecerta.data.local.dao

import androidx.room.*
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.local.entity.OccurrenceSuppression
import com.dosecerta.data.local.entity.ReconciliationCheckpoint
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.PharmaceuticalForm
import kotlinx.coroutines.flow.Flow

private const val FROZEN_DETAILS = "SELECT *, snapshotName AS medicationName, snapshotDosage AS dosage, snapshotUnit AS unit, snapshotColor AS color, snapshotForm AS pharmaceuticalForm, snapshotFrequency AS frequency, snapshotNotes AS medicationNotes FROM medication_logs "

@Dao
interface MedicationLogDao {
    @Query("SELECT * FROM medication_logs ORDER BY scheduledTime DESC, id DESC")
    fun getAllLogs(): Flow<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId ORDER BY scheduledTime DESC")
    fun getLogsForMedication(medicationId: Long): Flow<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId")
    suspend fun getLogsForMedicationSync(medicationId: Long): List<MedicationLog>

    @Query("SELECT * FROM medication_logs WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime AND state != 'CANCELLED' ORDER BY scheduledTime DESC")
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime AND status = :status AND state != 'CANCELLED' ORDER BY scheduledTime DESC")
    fun getLogsByStatusInRange(startTime: Long, endTime: Long, status: MedicationStatus): Flow<List<MedicationLog>>

    @Query("SELECT COUNT(*) FROM medication_logs WHERE status = :status AND scheduledTime >= :startTime AND state != 'CANCELLED'")
    fun getCountByStatus(status: MedicationStatus, startTime: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM medication_logs WHERE status = 'TAKEN' AND scheduledTime >= :startTime AND scheduledTime <= :endTime AND isScheduledDose = 1 AND isExtraDose = 0 AND state != 'CANCELLED'")
    suspend fun getTakenCountInRange(startTime: Long, endTime: Long): Int

    @Query("SELECT COUNT(*) FROM medication_logs WHERE status = :status AND scheduledTime >= :startTime AND scheduledTime <= :endTime AND isScheduledDose = 1 AND isExtraDose = 0 AND state != 'CANCELLED'")
    suspend fun getCountByStatusInRange(status: MedicationStatus, startTime: Long, endTime: Long): Int

    @Query("SELECT COUNT(*) FROM medication_logs WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime AND isScheduledDose = 1 AND isExtraDose = 0 AND state IN ('TAKEN','SKIPPED','MISSED')")
    suspend fun getTotalCountInRange(startTime: Long, endTime: Long): Int

    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId AND scheduleId = :scheduleId AND scheduledTime = :scheduledTime AND state != 'CANCELLED' LIMIT 1")
    suspend fun getLog(medicationId: Long, scheduleId: Long, scheduledTime: Long): MedicationLog?

    @Query("SELECT * FROM medication_logs WHERE occurrenceId = :occurrenceId LIMIT 1")
    suspend fun getOccurrence(occurrenceId: String): MedicationLog?

    @Query("SELECT * FROM medication_logs WHERE id = :id")
    suspend fun getById(id: Long): MedicationLog?

    @Query("SELECT * FROM medication_logs WHERE state IN ('PENDING','ALERTING','SNOOZED','DISMISSED') ORDER BY originalDueAt")
    suspend fun getPendingOccurrences(): List<MedicationLog>

    @Query("SELECT * FROM medication_logs WHERE state = 'MISSED' AND reminderAt IS NOT NULL ORDER BY reminderAt")
    suspend fun getFollowUpOccurrences(): List<MedicationLog>

    @Query(FROZEN_DETAILS + "WHERE state != 'CANCELLED' ORDER BY scheduledTime DESC, id DESC")
    fun getAllLogsWithDetails(): Flow<List<MedicationLogWithDetails>>

    @Query(FROZEN_DETAILS + "WHERE status = :status AND state != 'CANCELLED' ORDER BY scheduledTime DESC, id DESC")
    fun getAllLogsByStatusWithDetails(status: MedicationStatus): Flow<List<MedicationLogWithDetails>>

    @Query(FROZEN_DETAILS + "WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime AND state != 'CANCELLED' ORDER BY scheduledTime DESC, id DESC")
    fun getLogsInRangeWithDetails(startTime: Long, endTime: Long): Flow<List<MedicationLogWithDetails>>

    @Query(FROZEN_DETAILS + "WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime AND status = :status AND state != 'CANCELLED' ORDER BY scheduledTime DESC, id DESC")
    fun getLogsByStatusInRangeWithDetails(startTime: Long, endTime: Long, status: MedicationStatus): Flow<List<MedicationLogWithDetails>>

    /** Frozen half-open range; independent of the screen's latest Flow value. */
    @Query(FROZEN_DETAILS + "WHERE originalDueAt >= :startInclusive AND originalDueAt < :endExclusive AND state != 'CANCELLED' ORDER BY originalDueAt ASC, id ASC")
    suspend fun reportSnapshot(startInclusive: Long, endExclusive: Long): List<MedicationLogWithDetails>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: MedicationLog): Long

    @Update
    suspend fun update(log: MedicationLog)

    @Delete
    suspend fun delete(log: MedicationLog)

    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId")
    suspend fun deleteAllForMedication(medicationId: Long)

    @Query("UPDATE medication_logs SET state = 'CANCELLED', status = 'PENDING', snoozedUntil = NULL, reminderAt = NULL, schedulerHandle = NULL WHERE scheduleId = :scheduleId AND state IN ('PENDING','ALERTING','SNOOZED','DISMISSED')")
    suspend fun cancelPendingForSchedule(scheduleId: Long)

    @Query("UPDATE medication_logs SET schedulerHandle = :handle WHERE occurrenceId = :occurrenceId")
    suspend fun updateSchedulingHandle(occurrenceId: String, handle: String?)

    @Query("UPDATE medication_logs SET reminderAt = NULL WHERE occurrenceId = :occurrenceId")
    suspend fun clearReminder(occurrenceId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSuppression(suppression: OccurrenceSuppression)

    @Query("SELECT COUNT(*) FROM occurrence_suppressions WHERE occurrenceId = :occurrenceId")
    suspend fun suppressionCount(occurrenceId: String): Int

    @Query("SELECT reconciledUntil FROM reconciliation_checkpoint WHERE id = 1")
    suspend fun getReconciliationCheckpoint(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setReconciliationCheckpoint(checkpoint: ReconciliationCheckpoint)
}

data class MedicationLogWithDetails(
    @Embedded val log: MedicationLog,
    val medicationName: String?,
    val dosage: String?,
    val unit: String?,
    val color: Int?,
    val pharmaceuticalForm: PharmaceuticalForm? = null,
    val frequency: Frequency? = null,
    val medicationNotes: String? = null
)
