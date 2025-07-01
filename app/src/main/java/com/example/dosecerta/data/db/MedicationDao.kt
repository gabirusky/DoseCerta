package com.example.dosecerta.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.dosecerta.data.model.Medication

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long // Returns the new rowId

    @Update
    suspend fun updateMedication(medication: Medication)

    @Delete
    suspend fun deleteMedication(medication: Medication)

    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedications(): LiveData<List<Medication>> // Observe changes

    @Query("SELECT * FROM medications WHERE id = :medicationId")
    suspend fun getMedicationById(medicationId: Int): Medication?

    @Query("SELECT * FROM medications ORDER BY name ASC")
    suspend fun getAllMedicationsList(): List<Medication> // Non-LiveData version

    // You might add more specific queries later, e.g., search by name
} 