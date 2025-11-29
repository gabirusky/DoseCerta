package com.dosecerta.data.local.dao

import androidx.room.*
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for MedicationLog entity.
 */
@Dao
interface MedicationLogDao {
    
    @Query("SELECT * FROM medication_logs ORDER BY scheduledTime DESC")
    fun getAllLogs(): Flow<List<MedicationLog>>
    
    @Query("SELECT * FROM medication_logs WHERE medicationId = :medicationId ORDER BY scheduledTime DESC")
    fun getLogsForMedication(medicationId: Long): Flow<List<MedicationLog>>
    
    @Query("""
        SELECT * FROM medication_logs 
        WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime 
        ORDER BY scheduledTime DESC
    """)
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<MedicationLog>>
    
    @Query("""
        SELECT * FROM medication_logs 
        WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime AND status = :status
        ORDER BY scheduledTime DESC
    """)
    fun getLogsByStatusInRange(startTime: Long, endTime: Long, status: MedicationStatus): Flow<List<MedicationLog>>
    
    @Query("SELECT COUNT(*) FROM medication_logs WHERE status = :status AND scheduledTime >= :startTime")
    fun getCountByStatus(status: MedicationStatus, startTime: Long): Flow<Int>
    
    @Query("""
        SELECT COUNT(*) FROM medication_logs 
        WHERE status = 'TAKEN' AND scheduledTime >= :startTime AND scheduledTime <= :endTime
    """)
    suspend fun getTakenCountInRange(startTime: Long, endTime: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM medication_logs 
        WHERE status = :status AND scheduledTime >= :startTime AND scheduledTime <= :endTime
    """)
    suspend fun getCountByStatusInRange(status: MedicationStatus, startTime: Long, endTime: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM medication_logs 
        WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime
    """)
    suspend fun getTotalCountInRange(startTime: Long, endTime: Long): Int
    
    @Query("""
        SELECT * FROM medication_logs 
        WHERE medicationId = :medicationId AND scheduleId = :scheduleId AND scheduledTime = :scheduledTime
    """)
    suspend fun getLog(medicationId: Long, scheduleId: Long, scheduledTime: Long): MedicationLog?
    
    // Queries with medication details joined
    @Query("""
        SELECT ml.*, m.name as medicationName, m.dosage, m.unit 
        FROM medication_logs ml
        INNER JOIN medications m ON ml.medicationId = m.id
        ORDER BY ml.scheduledTime DESC
    """)
    fun getAllLogsWithDetails(): Flow<List<MedicationLogWithDetails>>
    
    @Query("""
        SELECT ml.*, m.name as medicationName, m.dosage, m.unit 
        FROM medication_logs ml
        INNER JOIN medications m ON ml.medicationId = m.id
        WHERE ml.scheduledTime >= :startTime AND ml.scheduledTime <= :endTime 
        ORDER BY ml.scheduledTime DESC
    """)
    fun getLogsInRangeWithDetails(startTime: Long, endTime: Long): Flow<List<MedicationLogWithDetails>>
    
    @Query("""
        SELECT ml.*, m.name as medicationName, m.dosage, m.unit 
        FROM medication_logs ml
        INNER JOIN medications m ON ml.medicationId = m.id
        WHERE ml.scheduledTime >= :startTime AND ml.scheduledTime <= :endTime AND ml.status = :status
        ORDER BY ml.scheduledTime DESC
    """)
    fun getLogsByStatusInRangeWithDetails(
        startTime: Long, 
        endTime: Long, 
        status: MedicationStatus
    ): Flow<List<MedicationLogWithDetails>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MedicationLog): Long
    
    @Update
    suspend fun update(log: MedicationLog)
    
    @Delete
    suspend fun delete(log: MedicationLog)
    
    @Query("DELETE FROM medication_logs WHERE medicationId = :medicationId")
    suspend fun deleteAllForMedication(medicationId: Long)
}

// Data class for joined query result
data class MedicationLogWithDetails(
    @Embedded val log: MedicationLog,
    val medicationName: String,
    val dosage: String,
    val unit: String
)
