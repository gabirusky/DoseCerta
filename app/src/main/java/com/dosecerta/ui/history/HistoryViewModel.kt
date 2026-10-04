package com.dosecerta.ui.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.AdherenceCalculator
import com.dosecerta.domain.AdherenceSummary
import com.dosecerta.domain.DoseActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.Locale

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: MedicationRepository, private val saved: SavedStateHandle, context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val _selectedFilter = saved.getStateFlow<MedicationStatus?>("filter", null)
    val selectedFilter: StateFlow<MedicationStatus?> = _selectedFilter
    val daysBack = saved.getStateFlow<Int?>("days", 7)
    private val refreshTrigger = MutableStateFlow(0)
    private val allPeriodLogs = combine(daysBack, refreshTrigger) { days, _ -> request(days, null) }
        .flatMapLatest { r -> repository.getLogsInRangeWithDetails(r.startInclusive, r.endExclusive - 1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = combine(allPeriodLogs, selectedFilter) { rows, filter -> rows.filter { filter == null || it.log.status == filter } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val statistics: StateFlow<AdherenceSummary> = allPeriodLogs.map { AdherenceCalculator.calculate(it.map { it.log }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AdherenceSummary(0, 0, 0, 0))
    private val _actionError = MutableStateFlow(false)
    val actionError = _actionError.asStateFlow()
    fun clearActionError() { _actionError.value = false }
    fun updateFilter(filter: MedicationStatus?) { saved["filter"] = filter }
    fun cyclePeriod() { saved["days"] = when (daysBack.value) { 7 -> 30; 30 -> null; else -> 7 } }
    fun refresh() { refreshTrigger.value++ }
    fun updateLogStatus(log: MedicationLog, newStatus: MedicationStatus, takenAt: Long?) {
        viewModelScope.launch {
            val result = repository.correctLog(log.id, newStatus, takenAt)
            _actionError.value = result !is DoseActionResult.Success
            if (result is DoseActionResult.Success) {
                com.dosecerta.alarm.AlarmScheduler(appContext).cancelOccurrence(log.occurrenceId)
                refresh()
            }
        }
    }
    fun deleteLog(log: MedicationLog) {
        viewModelScope.launch {
            runCatching { repository.deleteLog(log); com.dosecerta.alarm.AlarmScheduler(appContext).cancelOccurrence(log.occurrenceId) }.onFailure { _actionError.value = true }
            refresh()
        }
    }

    sealed class ExportState {
        object Idle : ExportState()
        object Choosing : ExportState()
        object Loading : ExportState()
        object Cancelled : ExportState()
        object Interrupted : ExportState()
        data class Success(val uri: Uri, val fileName: String) : ExportState()
        data class Error(val cleanupMayBeNeeded: Boolean = true) : ExportState()
    }
    private val _exportState = MutableStateFlow<ExportState>(when {
        saved.get<Uri>("completedUri") != null -> ExportState.Success(saved["completedUri"]!!, saved["fileName"] ?: "DoseCerta.pdf")
        saved.get<Uri>("destination") != null -> ExportState.Interrupted
        saved.get<ReportRequest>("request") != null -> ExportState.Choosing
        else -> ExportState.Idle
    })
    val exportState = _exportState.asStateFlow()
    fun beginExport(languageTag: String, zoneId: String): Boolean {
        if (_exportState.value is ExportState.Loading || _exportState.value is ExportState.Choosing) return false
        saved["request"] = request(daysBack.value, selectedFilter.value, languageTag, zoneId)
        saved["destination"] = null
        saved["completedUri"] = null
        _exportState.value = ExportState.Choosing
        return true
    }
    fun destinationSelected(uri: Uri?, appContext: Context) {
        if (uri == null) {
            saved["request"] = null
            _exportState.value = ExportState.Cancelled
            return
        }
        val r = saved.get<ReportRequest>("request") ?: run { _exportState.value = ExportState.Interrupted; return }
        if (_exportState.value is ExportState.Loading) return
        saved["destination"] = uri
        _exportState.value = ExportState.Loading
        val resolver = appContext.applicationContext.contentResolver
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val snapshot = ReportSnapshot.capture(repository, r)
                ReportDocuments.write(resolver, uri, snapshot)
                val name = runCatching { resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } }.getOrNull() ?: "DoseCerta.pdf"
                saved["completedUri"] = uri
                saved["fileName"] = name
                saved["destination"] = null
                saved["request"] = null
                _exportState.value = ExportState.Success(uri, name)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                saved["destination"] = null
                _exportState.value = ExportState.Error()
            }
        }
    }
    fun resetExportState() {
        if (_exportState.value is ExportState.Loading) return
        saved["request"] = null
        saved["destination"] = null
        saved["completedUri"] = null
        _exportState.value = ExportState.Idle
    }
    private fun request(days: Int?, status: MedicationStatus?, languageTag: String = Locale.getDefault().toLanguageTag(),
                        zoneName: String = ZoneId.systemDefault().id): ReportRequest {
        val now = System.currentTimeMillis()
        val zone = ZoneId.of(zoneName)
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ReportRequest(if (days == null) 0L else today.minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), zoneName, languageTag, status, now)
    }
}
