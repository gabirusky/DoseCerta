package com.dosecerta.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.model.ScheduleItem
import com.dosecerta.data.model.MedicationStatistics
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.util.DateTimeUtils
import kotlinx.coroutines.flow.*

/**
 * ViewModel for the Home screen.
 */
class HomeViewModel(private val repository: MedicationRepository) : ViewModel() {
    
    // Today's medication schedule
    val todaySchedule: StateFlow<List<ScheduleItem>> = flow {
        repository.getAllActiveSchedules().collect { schedules ->
            val today = DateTimeUtils.getStartOfToday()
            val endOfDay = DateTimeUtils.getEndOfToday()
            
            // Build schedule items for today
            val items = mutableListOf<ScheduleItem>()
            
            for (schedule in schedules) {
                // Check if this schedule should trigger today
                if (!DateTimeUtils.shouldScheduleToday(schedule.daysOfWeek)) continue
                
                val medication = repository.getMedicationByIdSync(schedule.medicationId) ?: continue
                val scheduledTime = DateTimeUtils.getTimestampForToday(schedule.timeInMinutes)
                
                // Check if log exists for this schedule today
                val log = repository.getLog(medication.id, schedule.id, scheduledTime)
                val status = log?.status ?: if (scheduledTime < System.currentTimeMillis()) {
                    MedicationStatus.MISSED
                } else {
                    MedicationStatus.PENDING
                }
                
                items.add(
                    ScheduleItem(
                        medication = medication,
                        schedule = schedule,
                        scheduledTime = scheduledTime,
                        status = status,
                        isPastDue = scheduledTime < System.currentTimeMillis() && status == MedicationStatus.PENDING
                    )
                )
            }
            
            // Sort by time
            emit(items.sortedBy { it.scheduledTime })
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Statistics
    val statistics: StateFlow<MedicationStatistics> = flow {
        val startOfWeek = DateTimeUtils.getStartOfWeek()
        val now = System.currentTimeMillis()
        
        val adherence = repository.calculateAdherence(startOfWeek, now)
        val takenCount = repository.getTakenCountInRange(startOfWeek, now)
        val totalCount = repository.getTotalCountInRange(startOfWeek, now)
        val activeCount = repository.getActiveMedicationCount().first()
        
        emit(
            MedicationStatistics(
                takenCount = activeCount,
                missedCount = 0,
                skippedCount = 0,
                adherencePercentage = adherence
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MedicationStatistics(0, 0, 0, 100)
    )
    
    /**
     * Mark medication as taken.
     */
    suspend fun markAsTaken(scheduleItem: ScheduleItem) {
        // Check if log already exists
        val existingLog = repository.getLog(
            scheduleItem.medication.id,
            scheduleItem.schedule.id,
            scheduleItem.scheduledTime
        )
        
        if (existingLog != null) {
            // Update existing log
            repository.updateLog(
                existingLog.copy(
                    status = MedicationStatus.TAKEN,
                    actualTime = System.currentTimeMillis()
                )
            )
        } else {
            // Create new log
            repository.insertLog(
                MedicationLog(
                    medicationId = scheduleItem.medication.id,
                    scheduleId = scheduleItem.schedule.id,
                    scheduledTime = scheduleItem.scheduledTime,
                    actualTime = System.currentTimeMillis(),
                    status = MedicationStatus.TAKEN
                )
            )
        }
    }
    
    /**
     * Snooze reminder for 10 minutes.
     */
    suspend fun snoozeReminder(scheduleItem: ScheduleItem) {
        // TODO: Reschedule alarm for 10 minutes from now
        // This will be implemented with AlarmScheduler
    }
}
