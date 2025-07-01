package com.example.dosecerta.data.repository

import androidx.lifecycle.LiveData
import com.example.dosecerta.data.db.MedicationDao
import com.example.dosecerta.data.db.MedicationLogDao
import com.example.dosecerta.data.db.ReminderDao
import com.example.dosecerta.data.model.FrequencyType
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.Medication
import com.example.dosecerta.data.model.MedicationLog
import com.example.dosecerta.data.model.Reminder
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

// The repository provides a clean API for data access to the rest of the application.
// It abstracts the data sources (in this case, only the Room database).
class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val reminderDao: ReminderDao,
    private val medicationLogDao: MedicationLogDao
) {

    // --- Medication Operations ---
    val allMedications: LiveData<List<Medication>> = medicationDao.getAllMedications()

    suspend fun getAllMedicationsList(): List<Medication> {
        return medicationDao.getAllMedicationsList()
    }

    suspend fun insertMedication(medication: Medication): Long {
        return medicationDao.insertMedication(medication)
    }

    suspend fun updateMedication(medication: Medication) {
        medicationDao.updateMedication(medication)
    }

    // Note: Deleting a medication will also delete associated reminders and logs due to CASCADE
    suspend fun deleteMedication(medication: Medication) {
        medicationDao.deleteMedication(medication)
    }

    suspend fun getMedicationById(medicationId: Int): Medication? {
        return medicationDao.getMedicationById(medicationId)
    }

    // --- Reminder Operations ---
    suspend fun getReminderById(reminderId: Int): Reminder? {
        return reminderDao.getReminderById(reminderId)
    }

    fun getRemindersForMedication(medicationId: Int): LiveData<List<Reminder>> {
        return reminderDao.getRemindersForMedication(medicationId)
    }

    suspend fun getRemindersForMedicationSync(medicationId: Int): List<Reminder> {
        return reminderDao.getRemindersForMedicationSync(medicationId)
    }

    suspend fun insertReminder(reminder: Reminder): Long {
        return reminderDao.insertReminder(reminder)
    }

    suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder)
    }

    suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun deleteRemindersForMedication(medicationId: Int) {
        reminderDao.deleteRemindersForMedication(medicationId)
    }

    suspend fun getAllEnabledReminders(): List<Reminder> {
        return reminderDao.getAllEnabledReminders()
    }

    // --- Medication Log Operations ---
    val allLogs: LiveData<List<MedicationLog>> = medicationLogDao.getAllLogs()

    suspend fun insertLog(log: MedicationLog): Long {
        return medicationLogDao.insertLog(log)
    }

    suspend fun getLogById(logId: Int): MedicationLog? {
        return medicationLogDao.getLogById(logId)
    }

    suspend fun updateLog(log: MedicationLog) {
        medicationLogDao.updateLog(log)
    }

    fun getLogsBetweenDates(startDate: Date, endDate: Date): LiveData<List<MedicationLog>> {
        return medicationLogDao.getLogsBetweenDates(startDate, endDate)
    }

    suspend fun getLogsBetweenDatesSync(startDate: Date, endDate: Date): List<MedicationLog> {
        return medicationLogDao.getLogsBetweenDatesSync(startDate, endDate)
    }

    fun getLogsByStatus(status: LogStatus): LiveData<List<MedicationLog>> {
        return medicationLogDao.getLogsByStatus(status)
    }

    fun getLogsByStatusAndDate(status: LogStatus, startDate: Date, endDate: Date): LiveData<List<MedicationLog>> {
        return medicationLogDao.getLogsByStatusAndDate(status, startDate, endDate)
    }

    fun getLogsForMedication(medicationId: Int): LiveData<List<MedicationLog>> {
        return medicationLogDao.getLogsForMedication(medicationId)
    }

    suspend fun countTakenLogsForMedication(medicationId: Int, startDate: Date, endDate: Date): Int {
        return medicationLogDao.countTakenLogsForMedication(medicationId, startDate, endDate)
    }

    suspend fun deleteLogsForMedication(medicationId: Int) {
        medicationLogDao.deleteLogsForMedication(medicationId)
    }

    // Method to update the status of a single log
    suspend fun updateLogStatus(logId: Long, newStatus: LogStatus) {
        medicationLogDao.updateLogStatus(logId, newStatus)
    }

    // Method to delete a single log by its ID
    suspend fun deleteLogById(logId: Long) {
        medicationLogDao.deleteLogById(logId)
    }

    // Method to find existing log for same medication and scheduled time
    suspend fun findLogByMedicationAndScheduledTime(medicationId: Int, scheduledTime: Date): MedicationLog? {
        return medicationLogDao.findLogByMedicationAndScheduledTime(medicationId, scheduledTime)
    }

    // Method to safely insert or update log, handling duplicates
    suspend fun insertOrUpdateLog(log: MedicationLog): Long {
        // Check for existing log at the same scheduled time
        val existingLog = findLogByMedicationAndScheduledTime(log.medicationId, log.scheduledTime ?: Date())
        
        return if (existingLog != null) {
            // Update existing log, prioritizing TAKEN status
            val updatedLog = if (log.status == LogStatus.TAKEN || existingLog.status != LogStatus.TAKEN) {
                existingLog.copy(
                    status = log.status,
                    logTimestamp = Date(),
                    medicationName = log.medicationName,
                    dosage = log.dosage
                )
            } else {
                existingLog // Keep existing if it's already TAKEN and new is not
            }
            updateLog(updatedLog)
            existingLog.id
        } else {
            insertLog(log)
        }
    }

    // Method to clean up duplicate logs
    suspend fun cleanupDuplicateLogs(medicationId: Int, scheduledTime: Date) {
        val duplicates = medicationLogDao.findDuplicateLogsForMedicationAndTime(medicationId, scheduledTime)
        if (duplicates.size > 1) {
            // Keep the first one (highest priority by status and most recent)
            val keepLog = duplicates.first()
            medicationLogDao.deleteDuplicateLogsExcept(medicationId, scheduledTime, keepLog.id)
        }
    }

    suspend fun getExpectedDosesForDay(day: Date): Int {
        val allMeds = getAllMedicationsList() // Fetch all medications
        val allReminders = getAllEnabledReminders().groupBy { it.medicationId } // Group reminders by med ID
        
        val calendarForDay = Calendar.getInstance().apply { time = day }
        val startOfDay = calendarForDay.startOfDay().timeInMillis
        val endOfDay = calendarForDay.endOfDay().timeInMillis

        var expectedCount = 0

        for (med in allMeds) {
            val medReminders = allReminders[med.id] ?: continue // Skip if no reminders for this med
            
            val isDoseExpectedToday = when (med.frequencyType) {
                FrequencyType.DAILY -> true // Always expected if daily
                FrequencyType.EVERY_X_DAYS -> {
                    val interval = med.frequencyIntervalDays
                    val startDate = med.startDate
                    if (interval == null || interval <= 0 || startDate == null) {
                        false // Cannot determine
                    } else {
                        val startCal = Calendar.getInstance().apply { time = startDate }.startOfDay()
                        val targetCal = Calendar.getInstance().apply { time = day }.startOfDay()
                        val daysBetween = TimeUnit.MILLISECONDS.toDays(targetCal.timeInMillis - startCal.timeInMillis)
                        (daysBetween >= 0) && (daysBetween % interval == 0L)
                    }
                }
                FrequencyType.SPECIFIC_DAYS_OF_WEEK -> {
                    val daysOfWeekString = med.frequencyDaysOfWeek
                    if (daysOfWeekString.isNullOrBlank()) {
                        false // Cannot determine
                    } else {
                        val targetDays = daysOfWeekString.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                        calendarForDay.get(Calendar.DAY_OF_WEEK) in targetDays
                    }
                }
                FrequencyType.AS_NEEDED -> false // Never expected automatically
            }

            if (isDoseExpectedToday) {
                expectedCount += medReminders.size // Add the number of reminders for this med
            }
        }
        
        return expectedCount
    }
}

// Helper extension functions needed within the Repository file or accessible to it
fun Calendar.startOfDay(): Calendar {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    return this
}

fun Calendar.endOfDay(): Calendar {
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
    return this
} 