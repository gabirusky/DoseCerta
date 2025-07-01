package com.example.dosecerta.ui.add_edit_med

import android.app.Application
import androidx.lifecycle.*
import com.example.dosecerta.data.model.DosageForm
import com.example.dosecerta.data.model.FrequencyType
import com.example.dosecerta.data.model.Medication
import com.example.dosecerta.data.model.Reminder
import com.example.dosecerta.data.model.StrengthUnit
import com.example.dosecerta.data.repository.MedicationRepository
import com.example.dosecerta.util.ReminderScheduler
import java.util.Date // Ensure Date is imported
import kotlinx.coroutines.launch

class AddEditMedViewModel(
        application: Application,
        private val repository: MedicationRepository,
        private val savedStateHandle: SavedStateHandle // Handles navigation arguments
) : AndroidViewModel(application) {

    // Get medication ID from navigation arguments, default is -1 (Add mode)
    private val medicationId: Int = savedStateHandle["medicationId"] ?: -1
    val isEditMode = medicationId != -1

    // LiveData to hold the medication being edited (if in edit mode)
    private val _medication = MutableLiveData<Medication?>()
    val medication: LiveData<Medication?> = _medication

    // Manage the list of reminder times locally before saving
    private val _reminders = MutableLiveData<MutableList<ReminderItem>>(mutableListOf())
    val reminders: LiveData<MutableList<ReminderItem>> = _reminders

    // LiveData to signal successful save and navigation back
    private val _navigateBack = MutableLiveData<Boolean>(false)
    val navigateBack: LiveData<Boolean> = _navigateBack

    // LiveData for validation errors (e.g., map field ID to error message)
    private val _validationErrors = MutableLiveData<Map<Int, String>>()
    val validationErrors: LiveData<Map<Int, String>> = _validationErrors

    init {
        if (isEditMode) {
            loadMedicationAndReminders()
        }
    }

    private fun loadMedicationAndReminders() {
        viewModelScope.launch {
            val med = repository.getMedicationById(medicationId)
            _medication.value = med
            med?.let {
                val existingReminders = repository.getRemindersForMedicationSync(it.id)
                _reminders.value =
                        existingReminders
                                .map { r -> ReminderItem(r.hour, r.minute) }
                                .toMutableList()
            }
        }
    }

    fun addReminder(hour: Int, minute: Int) {
        val newItem = ReminderItem(hour, minute)
        val currentList = _reminders.value ?: mutableListOf()
        // Avoid duplicates
        if (!currentList.contains(newItem)) {
            currentList.add(newItem)
            _reminders.value = currentList // Trigger LiveData update
        }
    }

    fun removeReminder(reminderItem: ReminderItem) {
        val currentList = _reminders.value ?: mutableListOf()
        currentList.remove(reminderItem)
        _reminders.value = currentList // Trigger LiveData update
    }

    fun saveMedication(
            name: String,
            dosage: String,
            dosageForm: DosageForm,
            strength: String?,
            strengthUnit: StrengthUnit?,
            notes: String?,
            // New frequency parameters
            frequencyType: FrequencyType,
            frequencyIntervalDays: Int?,
            frequencyDaysOfWeek: String?
    ) {
        if (validateInput(
                        name,
                        dosage,
                        dosageForm,
                        strength,
                        strengthUnit,
                        frequencyType,
                        frequencyIntervalDays,
                        frequencyDaysOfWeek
                )
        ) {
            viewModelScope.launch {
                val currentDate = Date() // Get current date for potential start date
                val medToSave =
                        Medication(
                                id = if (isEditMode) medicationId else 0,
                                name = name.trim(),
                                dosage = dosage.trim(),
                                dosageForm = dosageForm,
                                strength = strength?.trim()?.takeIf { it.isNotEmpty() },
                                strengthUnit =
                                        if (strength?.trim()?.isNotEmpty() == true) strengthUnit
                                        else null,
                                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                                // Save new frequency fields
                                frequencyType = frequencyType,
                                frequencyIntervalDays = frequencyIntervalDays,
                                frequencyDaysOfWeek =
                                        frequencyDaysOfWeek?.takeIf { it.isNotEmpty() },
                                // Set start date only if it's a NEW medication
                                startDate =
                                        if (isEditMode) _medication.value?.startDate
                                        else currentDate
                        )
                android.util.Log.d(
                        "AddEditMedViewModel",
                        "Medication startDate: ${medToSave.startDate}"
                )

                val savedMedId =
                        if (isEditMode) {
                            repository.updateMedication(medToSave)
                            medicationId // Use existing ID
                        } else {
                            repository.insertMedication(medToSave).toInt() // Get new ID
                        }

                if (savedMedId > 0) {
                    val oldReminders =
                            if (isEditMode) repository.getRemindersForMedicationSync(savedMedId)
                            else emptyList()
                    if (oldReminders.isNotEmpty()) {
                        ReminderScheduler.cancelReminders(getApplication(), oldReminders)
                    }
                    repository.deleteRemindersForMedication(savedMedId)

                    val remindersToSave =
                            _reminders.value?.map {
                                Reminder(
                                        medicationId = savedMedId,
                                        hour = it.hour,
                                        minute = it.minute,
                                        isEnabled = true
                                )
                            }
                                    ?: emptyList()

                    val savedRemindersWithIds = mutableListOf<Reminder>()
                    remindersToSave.forEach {
                        val newId = repository.insertReminder(it)
                        if (newId > 0) {
                            savedRemindersWithIds.add(it.copy(id = newId.toInt()))
                        }
                    }

                    // Schedule new alarms
                    if (savedRemindersWithIds.isNotEmpty()) {
                        ReminderScheduler.scheduleReminders(
                                getApplication(),
                                medToSave,
                                savedRemindersWithIds
                        )
                    }
                }
                _navigateBack.value = true
            }
        }
    }

    private fun validateInput(
            name: String,
            dosage: String,
            dosageForm: DosageForm?,
            strength: String?,
            strengthUnit: StrengthUnit?,
            // New frequency validation parameters
            frequencyType: FrequencyType?,
            frequencyIntervalDays: Int?,
            frequencyDaysOfWeek: String?
    ): Boolean {
        val errors = mutableMapOf<Int, String>()

        // Basic Fields
        if (name.trim().isEmpty()) errors[1] = "Medication name cannot be empty"
        if (dosage.trim().isEmpty()) errors[2] = "Dosage cannot be empty"
        if (dosageForm == null) errors[3] = "Please select a dosage form"

        // Strength/Unit Consistency
        val hasStrength = !strength.isNullOrBlank()
        val hasUnit = strengthUnit != null
        if (hasStrength && !hasUnit) errors[5] = "Please select a unit for the strength"
        else if (!hasStrength && hasUnit)
                errors[6] = "Please enter a strength for the selected unit"

        // Frequency Validation
        if (frequencyType == null) {
            errors[7] = "Please select frequency"
        } else {
            when (frequencyType) {
                FrequencyType.EVERY_X_DAYS -> {
                    if (frequencyIntervalDays == null || frequencyIntervalDays <= 0) {
                        errors[8] = "Interval must be a positive number"
                    }
                }
                FrequencyType.SPECIFIC_DAYS_OF_WEEK -> {
                    if (frequencyDaysOfWeek.isNullOrBlank()) {
                        errors[9] = "Please select at least one day"
                    }
                }
                FrequencyType.DAILY, FrequencyType.AS_NEEDED -> {
                    /* No extra validation needed */
                }
            }
        }

        _validationErrors.value = errors
        return errors.isEmpty()
    }

    // Call this when navigation is complete to reset the signal
    fun onNavigationComplete() {
        _navigateBack.value = false
    }
}
