package com.dosecerta.data.local.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.dosecerta.data.local.entity.Medication
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Medication entity.
 */
@Dao
interface MedicationDao {
    
    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY name ASC")
    fun getAllActiveMedications(): Flow<List<Medication>>
    
    @Query("SELECT * FROM medications WHERE id = :id")
    fun getMedicationById(id: Long): Flow<Medication?>
    
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationByIdSync(id: Long): Medication?
    
    @Query("SELECT * FROM medications WHERE isActive = 1 AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchMedications(query: String): Flow<List<Medication>>
    
    @Query("SELECT COUNT(*) FROM medications WHERE isActive = 1")
    fun getActiveMedicationCount(): Flow<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: Medication): Long
    
    @Update
    suspend fun update(medication: Medication)
    
    @Delete
    suspend fun delete(medication: Medication)
    
    @Query("UPDATE medications SET isActive = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
    
    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)
}
