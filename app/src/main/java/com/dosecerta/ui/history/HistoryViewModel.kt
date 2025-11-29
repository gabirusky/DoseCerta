package com.dosecerta.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.util.DateTimeUtils
import kotlinx.coroutines.flow.*

/**
 * ViewModel for the History screen.
 */
class HistoryViewModel(private val repository: MedicationRepository) : ViewModel() {
    
    // Selected filter
    private val _selectedFilter = MutableStateFlow<MedicationStatus?>(null)
    val selectedFilter: StateFlow<MedicationStatus?> = _selectedFilter.asStateFlow()
    
    // Date range (last 7 days by default)
    private val _daysBack = MutableStateFlow(7)
    val daysBack: StateFlow<Int> = _daysBack.asStateFlow()
    
    // Medication logs - reactively updates when logs change
    val logs: StateFlow<List<com.dosecerta.data.local.dao.MedicationLogWithDetails>> = combine(
        _daysBack,
        _selectedFilter
    ) { days, filter ->
        Pair(days, filter)
    }.flatMapLatest { (days, filter) ->
        val startTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val endTime = System.currentTimeMillis()
        
        if (filter != null) {
            repository.getLogsByStatusInRangeWithDetails(startTime, endTime, filter)
        } else {
            repository.getLogsInRangeWithDetails(startTime, endTime)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Statistics - derived from logs for reactive updates
    val statistics: StateFlow<Statistics> = logs.map { logsList ->
        val takenCount = logsList.count { it.log.status == MedicationStatus.TAKEN }
        val missedCount = logsList.count { it.log.status == MedicationStatus.MISSED }
        val skippedCount = logsList.count { it.log.status == MedicationStatus.SKIPPED }
        
        Statistics(
            taken = takenCount,
            missed = missedCount,
            skipped = skippedCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = Statistics(0, 0, 0)
    )
    
    fun updateFilter(filter: MedicationStatus?) {
        _selectedFilter.value = filter
    }
    
    fun updateDaysBack(days: Int) {
        _daysBack.value = days
    }
    
    data class Statistics(
        val taken: Int,
        val missed: Int,
        val skipped: Int
    )
}
