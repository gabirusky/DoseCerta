package com.dosecerta.ui.addmedication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for adding/editing medications.
 */
class AddMedicationViewModel(
    private val repository: MedicationRepository,
    private val medicationId: Long = -1L
) : ViewModel() {
    
    private var isEditMode = medicationId != -1L
    
    init {
        if (isEditMode) {
            loadMedication()
        }
    }
    
    private fun loadMedication() {
        viewModelScope.launch {
            val medication = repository.getMedicationByIdSync(medicationId) ?: return@launch
            _medicationName.value = medication.name
            _dosage.value = medication.dosage
            _unit.value = medication.unit
            _form.value = medication.pharmaceuticalForm
            _frequency.value = medication.frequency
            _notes.value = medication.notes ?: ""
            
            // Load existing schedules
            val schedules = repository.getSchedulesForMedicationSync(medicationId)
            _scheduleTimes.value = schedules.map { it.timeInMinutes }
        }
    }
    
    // Form state
    private val _medicationName = MutableStateFlow("")
    val medicationName: StateFlow<String> = _medicationName.asStateFlow()
    
    private val _dosage = MutableStateFlow("")
    val dosage: StateFlow<String> = _dosage.asStateFlow()
    
    private val _unit = MutableStateFlow("mg")
    val unit: StateFlow<String> = _unit.asStateFlow()
    
    private val _form = MutableStateFlow<PharmaceuticalForm>(PharmaceuticalForm.TABLET)
    val form: StateFlow<PharmaceuticalForm> = _form.asStateFlow()
    
    private val _frequency = MutableStateFlow<Frequency>(Frequency.DAILY)
    val frequency: StateFlow<Frequency> = _frequency.asStateFlow()
    
    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()
    
    private val _scheduleTimes = MutableStateFlow<List<Int>>(emptyList())
    val scheduleTimes: StateFlow<List<Int>> = _scheduleTimes.asStateFlow()
    
    // UI state
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()
    
    fun updateName(name: String) {
        _medicationName.value = name
    }
    
    fun updateDosage(dosage: String) {
        _dosage.value = dosage
    }
    
    fun updateUnit(unit: String) {
        _unit.value = unit
    }
    
    fun updateForm(form: PharmaceuticalForm) {
        _form.value = form
    }
    
    fun updateFrequency(frequency: Frequency) {
        _frequency.value = frequency
    }
    
    fun updateNotes(notes: String) {
        _notes.value = notes
    }
    
    fun addScheduleTime(timeInMinutes: Int) {
        _scheduleTimes.value = _scheduleTimes.value + timeInMinutes
    }
    
    fun removeScheduleTime(timeInMinutes: Int) {
        _scheduleTimes.value = _scheduleTimes.value - timeInMinutes
    }
    
    /**
     * Validate and save medication.
     */
    fun saveMedication() {
        viewModelScope.launch {
            try {
                _saveState.value = SaveState.Saving
                
                // Validation
                if (_medicationName.value.isBlank()) {
                    _saveState.value = SaveState.Error("Por favor, insira o nome do medicamento")
                    return@launch
                }
                
                if (_dosage.value.isBlank()) {
                    _saveState.value = SaveState.Error("Por favor, insira a dosagem")
                    return@launch
                }
                
                if (_scheduleTimes.value.isEmpty()) {
                    _saveState.value = SaveState.Error("Adicione pelo menos um horário")
                    return@launch
                }
                
                if (isEditMode) {
                    // Update existing medication
                    val medication = Medication(
                        id = medicationId,
                        name = _medicationName.value.trim(),
                        dosage = _dosage.value.trim(),
                        unit = _unit.value.trim(),
                        pharmaceuticalForm = _form.value,
                        frequency = _frequency.value,
                        notes = _notes.value.trim(),
                        isActive = true
                    )
                    
                    repository.updateMedication(medication)
                    
                    // Delete old schedules and create new ones
                    repository.deleteAllSchedulesForMedication(medicationId)
                    
                    val schedules = _scheduleTimes.value.map { timeInMinutes ->
                        Schedule(
                            medicationId = medicationId,
                            timeInMinutes = timeInMinutes,
                            daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                            isActive = true
                        )
                    }
                    
                    repository.insertSchedules(schedules)
                } else {
                    // Create new medication
                    val medication = Medication(
                        name = _medicationName.value.trim(),
                        dosage = _dosage.value.trim(),
                        unit = _unit.value.trim(),
                        pharmaceuticalForm = _form.value,
                        frequency = _frequency.value,
                        notes = _notes.value.trim(),
                        isActive = true
                    )
                    
                    val newMedicationId = repository.insertMedication(medication)
                    
                    // Create schedules
                    val schedules = _scheduleTimes.value.map { timeInMinutes ->
                        Schedule(
                            medicationId = newMedicationId,
                            timeInMinutes = timeInMinutes,
                            daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                            isActive = true
                        )
                    }
                    
                    repository.insertSchedules(schedules)
                }
                
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "Erro ao salvar medicamento")
            }
        }
    }
    
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }
    
    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        object Success : SaveState()
        data class Error(val message: String) : SaveState()
    }
}
