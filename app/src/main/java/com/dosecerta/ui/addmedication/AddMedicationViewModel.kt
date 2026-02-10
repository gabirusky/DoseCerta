package com.dosecerta.ui.addmedication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.model.ScheduleTime
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for adding/editing medications.
 */
class AddMedicationViewModel(
    private val repository: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val medicationId: Long = -1L
) : ViewModel() {
    
    val isEditMode = medicationId != -1L
    
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
            _color.value = medication.color
            
            // Load existing schedules
            val schedules = repository.getSchedulesForMedicationSync(medicationId)
            _scheduleTimes.value = schedules.map { ScheduleTime(it.id, it.timeInMinutes) }
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
    
    private val _color = MutableStateFlow(0xFF00897B.toInt())
    val color: StateFlow<Int> = _color.asStateFlow()
    
    private val _scheduleTimes = MutableStateFlow<List<ScheduleTime>>(emptyList())
    val scheduleTimes: StateFlow<List<ScheduleTime>> = _scheduleTimes.asStateFlow()
    
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
    
    fun updateColor(color: Int) {
        _color.value = color
    }
    
    fun addScheduleTime(timeInMinutes: Int) {
        // Add new time with ID 0
        _scheduleTimes.value = _scheduleTimes.value + ScheduleTime(0, timeInMinutes)
    }
    
    fun removeScheduleTime(scheduleTime: ScheduleTime) {
        _scheduleTimes.value = _scheduleTimes.value - scheduleTime
    }
    
    /**
     * Generate default reminder times based on frequency.
     * Replaces existing reminders with new times based on selected frequency.
     */
    fun generateDefaultReminders(frequency: Frequency) {
        // Don't generate for AS_NEEDED - clear reminders instead
        if (frequency == Frequency.AS_NEEDED) {
            _scheduleTimes.value = emptyList()
            return
        }
        
        val reminderCount = frequency.defaultReminderCount
        val intervalHours = frequency.intervalHours
        
        // Start at 8:00 AM (480 minutes from midnight)
        val startTimeMinutes = 8 * 60
        
        val newReminders = mutableListOf<ScheduleTime>()
        
        for (i in 0 until reminderCount) {
            // Calculate time: start from 8 AM and add interval * i hours
            val timeInMinutes = (startTimeMinutes + (intervalHours * i * 60)) % (24 * 60)
            newReminders.add(ScheduleTime(0, timeInMinutes))
        }
        
        _scheduleTimes.value = newReminders
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
                        color = _color.value,
                        isActive = true
                    )
                    
                    repository.updateMedication(medication)
                    
                    // Smart update of schedules to preserve logs
                    val currentSchedules = repository.getSchedulesForMedicationSync(medicationId)
                    val newScheduleTimes = _scheduleTimes.value
                    
                    // 1. Cancel ALL existing alarms first to prevent duplicates
                    alarmScheduler.cancelAlarmsForMedication(medicationId, currentSchedules)
                    
                    // 2. Delete removed schedules (alarm already cancelled above)
                    val newIds = newScheduleTimes.map { it.id }.filter { it != 0L }
                    val schedulesToDelete = currentSchedules.filter { it.id !in newIds }
                    
                    for (schedule in schedulesToDelete) {
                        repository.deleteSchedule(schedule)
                    }
                    
                    // 3. Update or Insert schedules
                    for (scheduleTime in newScheduleTimes) {
                        if (scheduleTime.id == 0L) {
                            // Insert new
                            val newSchedule = Schedule(
                                medicationId = medicationId,
                                timeInMinutes = scheduleTime.timeInMinutes,
                                daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                                isActive = true
                            )
                            repository.insertSchedule(newSchedule)
                        } else {
                            // Update existing
                            val existingSchedule = currentSchedules.find { it.id == scheduleTime.id }
                            if (existingSchedule != null && existingSchedule.timeInMinutes != scheduleTime.timeInMinutes) {
                                // Time changed - update schedule
                                repository.updateSchedule(existingSchedule.copy(timeInMinutes = scheduleTime.timeInMinutes))
                                
                                // Fix: Update log for today if it exists, so status is preserved
                                val oldScheduledTime = DateTimeUtils.getTimestampForToday(existingSchedule.timeInMinutes)
                                val log = repository.getLog(medicationId, existingSchedule.id, oldScheduledTime)
                                
                                if (log != null) {
                                    val newScheduledTime = DateTimeUtils.getTimestampForToday(scheduleTime.timeInMinutes)
                                    repository.updateLog(log.copy(scheduledTime = newScheduledTime))
                                }
                            }
                        }
                    }
                    
                    // 4. Fetch final schedules and schedule fresh alarms
                    val finalSchedules = repository.getSchedulesForMedicationSync(medicationId)
                    alarmScheduler.scheduleAlarmsForMedication(medicationId, finalSchedules)
                    
                } else {
                    // Create new medication
                    val medication = Medication(
                        name = _medicationName.value.trim(),
                        dosage = _dosage.value.trim(),
                        unit = _unit.value.trim(),
                        pharmaceuticalForm = _form.value,
                        frequency = _frequency.value,
                        notes = _notes.value.trim(),
                        color = _color.value,
                        isActive = true
                    )
                    
                    val newMedicationId = repository.insertMedication(medication)
                    
                    // Create schedules
                    val schedules = _scheduleTimes.value.map { scheduleTime ->
                        Schedule(
                            medicationId = newMedicationId,
                            timeInMinutes = scheduleTime.timeInMinutes,
                            daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                            isActive = true
                        )
                    }
                    
                    repository.insertSchedules(schedules)
                    
                    // Fetch schedules from database to get their generated IDs
                    val insertedSchedules = repository.getSchedulesForMedicationSync(newMedicationId)
                    
                    // Schedule alarms with the actual schedule IDs from database
                    alarmScheduler.scheduleAlarmsForMedication(newMedicationId, insertedSchedules)
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
