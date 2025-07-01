package com.example.dosecerta.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.FrequencyType
import com.example.dosecerta.data.model.MedicationLog
import com.example.dosecerta.data.repository.MedicationRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// Data class for Adherence Chart
data class AdherenceData(val dayLabel: String, val adherencePercentage: Float)

class HomeViewModel(private val repository: MedicationRepository) : ViewModel() {

    private val _todaySchedule = MutableLiveData<List<ScheduleItem>>(emptyList())
    val todaySchedule: LiveData<List<ScheduleItem>> = _todaySchedule

    private val _adherenceData = MutableLiveData<List<AdherenceData>>(emptyList())
    val adherenceData: LiveData<List<AdherenceData>> = _adherenceData

    private val _currentDate = MutableLiveData<String>()
    val currentDate: LiveData<String> = _currentDate

    private val dayFormatter = SimpleDateFormat("EEE, MMM d", Locale.getDefault()) // e.g., Tue, Apr 1
    private val shortDayFormatter = SimpleDateFormat("EEE", Locale.getDefault()) // e.g., Tue

    init {
        _currentDate.value = dayFormatter.format(Date())
        loadHomePageData()
    }

    fun loadHomePageData() {
        viewModelScope.launch {
            Log.d("HomeViewModel", "Loading home page data...")
            val enabledReminders = repository.getAllEnabledReminders()
            Log.d("HomeViewModel", "Enabled reminders: $enabledReminders")
            val medicationMap = repository.getAllMedicationsList()
                                      .associateBy { it.id }
            Log.d("HomeViewModel", "Medication map: $medicationMap")

            val now = Calendar.getInstance()
            val todayStart = now.startOfDay().time
            val todayEnd = now.endOfDay().time

            // Fetch today\'s logs to check status
            val logsTodayList = repository.getLogsBetweenDatesSync(todayStart, todayEnd)
            Log.d("HomeViewModel", "Logs today: $logsTodayList")
            val logsToday = logsTodayList
                .groupBy { log ->
                    val scheduledCal = Calendar.getInstance().apply {
                        time = log.scheduledTime ?: return@groupBy Triple(-1, -1, -1)
                    }
                    Triple(log.medicationId, scheduledCal.get(Calendar.HOUR_OF_DAY), scheduledCal.get(Calendar.MINUTE))
                }
                .mapValues { (_, logs) ->
                    // If multiple logs exist for same time, prefer TAKEN > SKIPPED > MISSED
                    logs.maxByOrNull { log ->
                        when (log.status) {
                            LogStatus.TAKEN -> 3
                            LogStatus.SKIPPED -> 2
                            LogStatus.MISSED -> 1
                            else -> 0
                        }
                    }
                }
            
            val scheduleItems = enabledReminders.mapNotNull { reminder ->
                medicationMap[reminder.medicationId]?.let { med ->
                    Log.d("HomeViewModel", "Processing reminder: $reminder, medication: $med")
                    // Determine initial status from today\'s logs
                    val reminderTime = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, reminder.hour)
                        set(Calendar.MINUTE, reminder.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val logKey = Triple(med.id, reminderTime.get(Calendar.HOUR_OF_DAY), reminderTime.get(Calendar.MINUTE))
                    val existingLog = logsToday[logKey]

                    ScheduleItem(
                        reminderId = reminder.id,
                        medicationId = med.id,
                        medicationName = med.name,
                        dosage = med.dosage,
                        hour = reminder.hour,
                        minute = reminder.minute,
                        frequencyType = med.frequencyType,
                        status = existingLog?.status // Set initial status
                    )
                }
            }.sortedWith(compareBy({ it.hour }, { it.minute }))
            
            // Remove filter: Show ALL items scheduled for today initially.
            // The adapter will visually handle items already marked as TAKEN.
            // _todaySchedule.value = scheduleItems.filter { it.status == null || it.status == LogStatus.SKIPPED }
            _todaySchedule.value = scheduleItems 
            
            calculateAdherence()
        }
    }

    private suspend fun calculateAdherence() {
        val adherenceList = mutableListOf<AdherenceData>()
        val calendar = Calendar.getInstance()
        
        // Loop through the last 7 days (including today)
        for (i in 6 downTo 0) {
            calendar.time = Date() // Reset to today
            calendar.add(Calendar.DAY_OF_YEAR, -i) // Go back i days
            val dayStart = calendar.startOfDay().time
            val dayEnd = calendar.endOfDay().time
            val dayLabel = shortDayFormatter.format(calendar.time)

            // Fetch logs for this day
            val logsToday = repository.getLogsBetweenDatesSync(dayStart, dayEnd) // Need suspend fun
            val takenCount = logsToday.count { it.status == LogStatus.TAKEN }
            
            // Estimate expected doses for the day (This is complex and needs refinement!)
            // Simplistic approach: Count unique reminder times scheduled for that day
            // A better way might involve looking at medication frequency directly.
            val expectedCount = repository.getExpectedDosesForDay(calendar.time) // Needs complex DAO/Repo method

            val adherencePercent = if (expectedCount > 0) {
                (takenCount.toFloat() / expectedCount.toFloat() * 100f)
            } else {
                100f // Assume 100% if no doses were expected
            }
            
            adherenceList.add(AdherenceData(dayLabel, adherencePercent.roundToInt().toFloat()))
        }
        _adherenceData.value = adherenceList
    }
    
     // Helper to log taken dose from Home screen
    fun logDose(item: ScheduleItem, status: LogStatus) {
        viewModelScope.launch {
            val medication = repository.getMedicationById(item.medicationId)
            if (medication != null) {
                val scheduledTime = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, item.hour)
                    set(Calendar.MINUTE, item.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
                
                // For AS_NEEDED medications, always create new logs when TAKEN
                if (item.frequencyType == FrequencyType.AS_NEEDED && status == LogStatus.TAKEN) {
                    val newLog = MedicationLog(
                        medicationId = item.medicationId,
                        medicationName = item.medicationName,
                        dosage = item.dosage,
                        scheduledTime = scheduledTime,
                        logTimestamp = Date(),
                        status = status
                    )
                    repository.insertLog(newLog)
                } else {
                    // For scheduled medications, use safe insert/update to prevent duplicates
                    val logToSave = MedicationLog(
                        medicationId = item.medicationId,
                        medicationName = item.medicationName,
                        dosage = item.dosage,
                        scheduledTime = scheduledTime,
                        logTimestamp = Date(),
                        status = status
                    )
                    repository.insertOrUpdateLog(logToSave)
                    
                    // Clean up any duplicates that might exist
                    repository.cleanupDuplicateLogs(item.medicationId, scheduledTime)
                }

                // Reload the home page data to reflect the change
                loadHomePageData()
            }
        }
    }

    // New function to handle undoing a skip action
    fun undoSkip(item: ScheduleItem) {
        viewModelScope.launch {
            val scheduledTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, item.hour)
                set(Calendar.MINUTE, item.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            
            val skippedLog = repository.findLogByMedicationAndScheduledTime(item.medicationId, scheduledTime)
             
            if (skippedLog != null && skippedLog.status == LogStatus.SKIPPED) {
                // Delete the skipped log entry to allow rescheduling
                repository.deleteLogById(skippedLog.id)
                 
                Log.d("HomeViewModel", "Undid skip for reminderId: ${item.reminderId}")
                // Refresh the list to show the item as pending again
                loadHomePageData()
            } else {
                Log.w("HomeViewModel", "Could not find SKIPPED log to undo for reminderId: ${item.reminderId}")
            }
        }
    }

    // Helper to find an existing log matching a schedule item for today
    private suspend fun findLogForScheduleItem(item: ScheduleItem): MedicationLog? {
        val scheduledTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, item.hour)
            set(Calendar.MINUTE, item.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        return repository.findLogByMedicationAndScheduledTime(item.medicationId, scheduledTime)
    }
}

// Helper extension functions for Calendar
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