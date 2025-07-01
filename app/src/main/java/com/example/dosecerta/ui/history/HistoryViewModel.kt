package com.example.dosecerta.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.switchMap

import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.MedicationLog
import com.example.dosecerta.data.repository.MedicationRepository
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class HistoryViewModel(private val repository: MedicationRepository) : ViewModel() {

    private val _filterType = MutableLiveData<LogStatus?>() // null means 'All'
    private val _dateRange = MutableLiveData<Pair<Date, Date>?>(null) // null means 'All Time'

    // LiveData for the filtered logs, reacts to changes in filter type and date range
    val filteredLogs: LiveData<List<MedicationLog>> = _filterType.switchMap { status ->
        _dateRange.switchMap { range ->
            when {
                status != null && range != null -> repository.getLogsByStatusAndDate(status, range.first, range.second)
                status != null && range == null -> repository.getLogsByStatus(status)
                status == null && range != null -> repository.getLogsBetweenDates(range.first, range.second)
                else -> repository.allLogs // Both null, show all
            }
        }
    }

    init {
        // Set initial filter to 'All'
        _filterType.value = null
        // Optionally set an initial date range, e.g., last 30 days
        // setDateRange(...) 
    }

    fun setFilterType(status: LogStatus?) {
        if (_filterType.value != status) {
            _filterType.value = status
        }
    }

    fun setDateRange(startDate: Date?, endDate: Date?) {
        val newRange = if (startDate != null && endDate != null) Pair(startDate, endDate) else null
        if (_dateRange.value != newRange) {
             _dateRange.value = newRange
        }
    }

    // Example function to set range to last 7 days
    fun setDateRangeLast7Days() {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time // Today
        calendar.add(Calendar.DAY_OF_YEAR, -6) // Go back 6 days to include today (7 days total)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startDate = calendar.time
        setDateRange(startDate, endDate)
    }

    // Function to update the status of a specific log entry
    fun updateLogStatus(logId: Long, newStatus: LogStatus) {
        viewModelScope.launch {
            repository.updateLogStatus(logId, newStatus)
            // No need to manually refresh LiveData; switchMap handles it
        }
    }

    // Function to delete a specific log entry
    fun deleteLog(logId: Long) {
        viewModelScope.launch {
            repository.deleteLogById(logId)
             // No need to manually refresh LiveData; switchMap handles it
        }
    }
}