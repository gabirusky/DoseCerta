package com.dosecerta.ui.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.util.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the History screen.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: MedicationRepository) : ViewModel() {
    
    // Selected filter
    private val _selectedFilter = MutableStateFlow<MedicationStatus?>(null)
    val selectedFilter: StateFlow<MedicationStatus?> = _selectedFilter.asStateFlow()
    
    // Date range: 7 or 30 days, or null = all time (Todo o Período)
    private val _daysBack = MutableStateFlow<Int?>(7)
    val daysBack: StateFlow<Int?> = _daysBack.asStateFlow()
    
    // Refresh trigger - increment to force data reload
    private val _refreshTrigger = MutableStateFlow(0)
    
    // Medication logs - reactively updates when logs change
    val logs: StateFlow<List<com.dosecerta.data.local.dao.MedicationLogWithDetails>> = combine(
        _daysBack,
        _selectedFilter,
        _refreshTrigger
    ) { days, filter, _ ->
        Pair(days, filter)
    }.flatMapLatest { (days, filter) ->
        if (days == null) {
            // All-time mode: no date restriction
            if (filter != null) {
                repository.getAllLogsByStatusWithDetails(filter)
            } else {
                repository.getAllLogsWithDetails()
            }
        } else {
            val startTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            val endTime = System.currentTimeMillis()
            if (filter != null) {
                repository.getLogsByStatusInRangeWithDetails(startTime, endTime, filter)
            } else {
                repository.getLogsInRangeWithDetails(startTime, endTime)
            }
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
    
    fun cyclePeriod() {
        _daysBack.value = when (_daysBack.value) {
            7 -> 30
            30 -> null  // all time
            else -> 7
        }
    }
    
    /**
     * Refresh the medication logs and statistics from the database.
     * This forces a reload of all data.
     */
    fun refresh() {
        _refreshTrigger.value += 1
    }
    
    /**
     * Update the status of a medication log.
     */
    fun updateLogStatus(log: MedicationLog, newStatus: MedicationStatus) {
        viewModelScope.launch {
            val updatedLog = log.copy(
                status = newStatus,
                actualTime = if (newStatus == MedicationStatus.TAKEN) System.currentTimeMillis() else log.actualTime
            )
            repository.updateLog(updatedLog)
            refresh()
        }
    }
    
    /**
     * Delete a medication log entry.
     */
    fun deleteLog(log: MedicationLog) {
        viewModelScope.launch {
            repository.deleteLog(log)
            refresh()
        }
    }
    
    // ─── PDF Export ──────────────────────────────────────────────────────────

    sealed class ExportState {
        object Idle    : ExportState()
        object Loading : ExportState()
        data class Success(val uri: Uri, val fileName: String) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun exportPdf(context: Context, periodLabel: String) {
        if (_exportState.value is ExportState.Loading) return
        _exportState.value = ExportState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentLogs = logs.value
                val generator = PdfReportGenerator(context)
                val uri = generator.generate(currentLogs, periodLabel)
                val name = uri.lastPathSegment ?: "relatorio.pdf"
                _exportState.value = ExportState.Success(uri, name)
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(e.message ?: "Erro ao gerar PDF")
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }

    data class Statistics(
        val taken: Int,
        val missed: Int,
        val skipped: Int
    )
}
