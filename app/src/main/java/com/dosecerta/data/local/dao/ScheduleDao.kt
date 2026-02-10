package com.dosecerta.data.local.dao

import androidx.room.*
import com.dosecerta.data.local.entity.Schedule
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Schedule entity.
 */
@Dao
interface ScheduleDao {
    
    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId AND isActive = 1")
    fun getSchedulesForMedication(medicationId: Long): Flow<List<Schedule>>
    
    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId AND isActive = 1")
    suspend fun getSchedulesForMedicationSync(medicationId: Long): List<Schedule>
    
    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): Schedule?
    
    @Query("SELECT * FROM schedules WHERE isActive = 1")
    fun getAllActiveSchedules(): Flow<List<Schedule>>
    
    @Query("SELECT * FROM schedules WHERE isActive = 1")
    suspend fun getAllActiveSchedulesSync(): List<Schedule>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: Schedule): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<Schedule>)
    
    @Update
    suspend fun update(schedule: Schedule)
    
    @Delete
    suspend fun delete(schedule: Schedule)
    
    @Query("DELETE FROM schedules WHERE medicationId = :medicationId")
    suspend fun deleteAllForMedication(medicationId: Long)
    
    @Query("UPDATE schedules SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}
