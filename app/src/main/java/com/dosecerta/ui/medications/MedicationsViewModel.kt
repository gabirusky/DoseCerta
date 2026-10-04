package com.dosecerta.ui.medications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.repository.MedicationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

class MedicationsViewModel(private val repository: MedicationRepository, private val alarmScheduler: AlarmScheduler,
    private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    val searchQuery = savedState.getStateFlow("search", "")
    val selectedFilter = savedState.getStateFlow<String?>("filter", null)
    private val allMedications = repository.getAllActiveMedications()
    val medications = combine(allMedications, searchQuery, selectedFilter) { meds, query, filter ->
        meds.filter { (query.isBlank() || it.name.contains(query, ignoreCase = true)) && (filter == null || it.frequency.name == filter) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val clock = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }
    val upcoming = combine(repository.getAllActiveSchedules(), clock) { schedules, now ->
        schedules.groupBy { it.medicationId }.mapValues { (_, slots) -> slots.mapNotNull { repository.previewOccurrences(it, 1, Instant.ofEpochMilli(now)).firstOrNull()?.originalDueAt }.minOrNull() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    private val _operation = MutableStateFlow<Operation>(Operation.Idle)
    val operation = _operation.asStateFlow()
    sealed class Operation { data object Idle : Operation(); data object Busy : Operation(); data class Done(val archived: Boolean) : Operation(); data object Error : Operation() }
    fun updateSearchQuery(value: String) { savedState["search"] = value }
    fun updateFilter(value: Frequency?) { savedState["filter"] = value?.name }
    fun consumeOperation() { _operation.value = Operation.Idle }
    fun archiveMedication(medication: Medication) = mutate(medication, null)
    fun deleteMedication(medication: Medication, deleteHistory: Boolean) = mutate(medication, deleteHistory)
    private fun mutate(medication: Medication, deleteHistory: Boolean?) {
        if (_operation.value == Operation.Busy) return
        _operation.value = Operation.Busy
        viewModelScope.launch {
            try {
                val schedules = repository.getSchedulesForMedicationSync(medication.id)
                // Stop every handle while identities still exist. Repository then commits archive/delete.
                alarmScheduler.cancelAlarmsForMedication(medication.id, schedules)
                if (deleteHistory == null) repository.archiveMedication(medication.id)
                else repository.permanentlyDeleteMedication(medication.id, deleteHistory)
                _operation.value = Operation.Done(deleteHistory == null)
            } catch (e: Exception) {
                // Failed deletion keeps the medication actionable; restore its alarms if still active.
                runCatching { val slots = repository.getSchedulesForMedicationSync(medication.id); alarmScheduler.scheduleAlarmsForMedication(medication.id, slots) }
                _operation.value = Operation.Error
            }
        }
    }
}
