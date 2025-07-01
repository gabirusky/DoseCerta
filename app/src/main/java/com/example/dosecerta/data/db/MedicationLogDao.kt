package com.example.dosecerta.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.MedicationLog
import java.util.Date

@Dao
interface MedicationLogDao {
    @Insert
    suspend fun insertLog(log: MedicationLog): Long

    @Query("SELECT * FROM medication_logs ORDER BY logTimestamp DESC")
    fun getAllLogs(): LiveData<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE logTimestamp BETWEEN :startDate AND :endDate ORDER BY logTimestamp DESC")
    fun getLogsBetweenDates(startDate: Date, endDate: Date): LiveData<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE logTimestamp BETWEEN :startDate AND :endDate")
    suspend fun getLogsBetweenDatesSync(startDate: Date, endDate: Date): List<MedicationLog> // Non-LiveData version

    @Query("SELECT * FROM medication_logs WHERE status = :status ORDER BY logTimestamp DESC")
    fun getLogsByStatus(status: LogStatus): LiveData<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE status = :status AND logTimestamp BETWEEN :startDate AND :endDate ORDER BY logTimestamp DESC")
    fun getLogsByStatusAndDate(status: LogStatus, startDate: Date, endDate: Date): LiveData<List<MedicationLog>>

    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId ORDER BY logTimestamp DESC")
    fun getLogsForMedication(medicationId: Int): LiveData<List<MedicationLog>>

    // Query for adherence calculation (e.g., count taken logs in a period)
    @Query("SELECT COUNT(*) FROM medication_logs WHERE medicationId = :medicationId AND status = 'TAKEN' AND logTimestamp BETWEEN :startDate AND :endDate")
    suspend fun countTakenLogsForMedication(medicationId: Int, startDate: Date, endDate: Date): Int

    // Query to count all scheduled/expected doses in a period (might need more complex logic depending on frequency)
    // This might be better calculated outside the DB based on medication frequency and reminders.

    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId")
    suspend fun deleteLogsForMedication(medicationId: Int)

    @Query("DELETE FROM medication_logs")
    suspend fun deleteAllLogs() // For potential testing or data reset

    @Query("SELECT * FROM medication_logs WHERE id = :logId")
    suspend fun getLogById(logId: Int): MedicationLog?

    @Update
    suspend fun updateLog(log: MedicationLog)

    @Query("UPDATE medication_logs SET status = :newStatus WHERE id = :logId")
    suspend fun updateLogStatus(logId: Long, newStatus: LogStatus)

    @Query("DELETE FROM medication_logs WHERE id = :logId")
    suspend fun deleteLogById(logId: Long)

    // Method to find existing log for same medication and scheduled time
    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime LIMIT 1")
    suspend fun findLogByMedicationAndScheduledTime(medicationId: Int, scheduledTime: Date): MedicationLog?

    // Method to find duplicate logs (same medicationId and scheduledTime but different IDs)
    @Query("""
        SELECT * FROM medication_logs 
        WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime 
        ORDER BY 
          CASE 
            WHEN status = 'TAKEN' THEN 1 
            WHEN status = 'SKIPPED' THEN 2 
            WHEN status = 'MISSED' THEN 3 
          END,
          logTimestamp DESC
    """)
    suspend fun findDuplicateLogsForMedicationAndTime(medicationId: Int, scheduledTime: Date): List<MedicationLog>

    // Method to clean up duplicate logs, keeping only the most relevant one
    @Query("""
        DELETE FROM medication_logs 
        WHERE medicationId = :medicationId AND scheduledTime = :scheduledTime AND id != :keepId
    """)
    suspend fun deleteDuplicateLogsExcept(medicationId: Int, scheduledTime: Date, keepId: Long)
} 