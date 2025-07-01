package com.example.dosecerta.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.dosecerta.data.model.Reminder

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("SELECT * FROM reminders WHERE medicationId = :medicationId ORDER BY hour, minute ASC")
    fun getRemindersForMedication(medicationId: Int): LiveData<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE medicationId = :medicationId ORDER BY hour, minute ASC")
    suspend fun getRemindersForMedicationSync(medicationId: Int): List<Reminder> // Non-LiveData version

    @Query("SELECT * FROM reminders WHERE id = :reminderId")
    suspend fun getReminderById(reminderId: Int): Reminder?

    @Query("DELETE FROM reminders WHERE medicationId = :medicationId")
    suspend fun deleteRemindersForMedication(medicationId: Int)

    @Query("SELECT * FROM reminders WHERE isEnabled = 1") // Get all active reminders for scheduling
    suspend fun getAllEnabledReminders(): List<Reminder>

} 