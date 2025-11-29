package com.dosecerta.data.repository

import com.dosecerta.data.local.dao.MedicationDao
import com.dosecerta.data.local.dao.MedicationLogDao
import com.dosecerta.data.local.dao.ScheduleDao
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.MedicationStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for medication-related data operations.
 * Provides a clean API for the UI layer to interact with the data layer.
 */
class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val medicationLogDao: MedicationLogDao
) {
    
    // Medication operations
    fun getAllActiveMedications(): Flow<List<Medication>> = medicationDao.getAllActiveMedications()
    
    fun getMedicationById(id: Long): Flow<Medication?> = medicationDao.getMedicationById(id)
    
    suspend fun getMedicationByIdSync(id: Long): Medication? = medicationDao.getMedicationByIdSync(id)
    
    fun searchMedications(query: String): Flow<List<Medication>> = medicationDao.searchMedications(query)
    
    fun getActiveMedicationCount(): Flow<Int> = medicationDao.getActiveMedicationCount()
    
    suspend fun insertMedication(medication: Medication): Long = medicationDao.insert(medication)
    
    suspend fun updateMedication(medication: Medication) = medicationDao.update(medication)
    
    suspend fun deleteMedication(medication: Medication) {
        medicationDao.delete(medication)
    }
    
    suspend fun deleteMedicationById(id: Long) {
        medicationDao.deleteById(id)
    }
    
    // Schedule operations
    fun getSchedulesForMedication(medicationId: Long): Flow<List<Schedule>> = 
        scheduleDao.getSchedulesForMedication(medicationId)
    
    suspend fun getSchedulesForMedicationSync(medicationId: Long): List<Schedule> = 
        scheduleDao.getSchedulesForMedicationSync(medicationId)
    
    fun getAllActiveSchedules(): Flow<List<Schedule>> = scheduleDao.getAllActiveSchedules()
    
    suspend fun getAllActiveSchedulesSync(): List<Schedule> = scheduleDao.getAllActiveSchedulesSync()
    
    suspend fun insertSchedule(schedule: Schedule): Long = scheduleDao.insert(schedule)
    
    suspend fun insertSchedules(schedules: List<Schedule>) = scheduleDao.insertAll(schedules)
    
    suspend fun updateSchedule(schedule: Schedule) = scheduleDao.update(schedule)
    
    suspend fun deleteSchedule(schedule: Schedule) = scheduleDao.delete(schedule)
    
    suspend fun deleteAllSchedulesForMedication(medicationId: Long) = 
        scheduleDao.deleteAllForMedication(medicationId)
    
    // Medication log operations
    fun getAllLogs(): Flow<List<MedicationLog>> = medicationLogDao.getAllLogs()
    
    fun getLogsForMedication(medicationId: Long): Flow<List<MedicationLog>> = 
        medicationLogDao.getLogsForMedication(medicationId)
    
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<MedicationLog>> = 
        medicationLogDao.getLogsInRange(startTime, endTime)
    
    fun getLogsByStatusInRange(
        startTime: Long,
        endTime: Long,
        status: MedicationStatus
    ): Flow<List<MedicationLog>> = 
        medicationLogDao.getLogsByStatusInRange(startTime, endTime, status)
    
    // Medication log operations with details (includes medication name, dosage, etc.)
    fun getAllLogsWithDetails(): Flow<List<com.dosecerta.data.local.dao.MedicationLogWithDetails>> = 
        medicationLogDao.getAllLogsWithDetails()
    
    fun getLogsInRangeWithDetails(startTime: Long, endTime: Long): Flow<List<com.dosecerta.data.local.dao.MedicationLogWithDetails>> = 
        medicationLogDao.getLogsInRangeWithDetails(startTime, endTime)
    
    fun getLogsByStatusInRangeWithDetails(
        startTime: Long,
        endTime: Long,
        status: MedicationStatus
    ): Flow<List<com.dosecerta.data.local.dao.MedicationLogWithDetails>> = 
        medicationLogDao.getLogsByStatusInRangeWithDetails(startTime, endTime, status)
    
    fun getCountByStatus(status: MedicationStatus, startTime: Long): Flow<Int> = 
       medicationLogDao.getCountByStatus(status, startTime)
    
    suspend fun getTakenCountInRange(startTime: Long, endTime: Long): Int = 
        medicationLogDao.getTakenCountInRange(startTime, endTime)
    
    suspend fun getMissedCountInRange(startTime: Long, endTime: Long): Int = 
        medicationLogDao.getCountByStatusInRange(MedicationStatus.MISSED, startTime, endTime)
    
    suspend fun getSkippedCountInRange(startTime: Long, endTime: Long): Int = 
        medicationLogDao.getCountByStatusInRange(MedicationStatus.SKIPPED, startTime, endTime)
    
    suspend fun getTotalCountInRange(startTime: Long, endTime: Long): Int = 
        medicationLogDao.getTotalCountInRange(startTime, endTime)
    
    suspend fun getLog(medicationId: Long, scheduleId: Long, scheduledTime: Long): MedicationLog? = 
        medicationLogDao.getLog(medicationId, scheduleId, scheduledTime)
    
    suspend fun insertLog(log: MedicationLog): Long = medicationLogDao.insert(log)
    
    suspend fun updateLog(log: MedicationLog) = medicationLogDao.update(log)
    
    suspend fun deleteLog(log: MedicationLog) = medicationLogDao.delete(log)
    
    /**
     * Calculate adherence percentage for a time range.
     * @return Percentage (0-100) of medications taken vs. scheduled.
     */
    suspend fun calculateAdherence(startTime: Long, endTime: Long): Int {
        val takenCount = getTakenCountInRange(startTime, endTime)
        val totalCount = getTotalCountInRange(startTime, endTime)
        return if (totalCount > 0) {
            ((takenCount.toFloat() / totalCount) * 100).toInt()
        } else {
            100 // Default to 100% if no logs
        }
    }
}
