package com.example.dosecerta.ui.meds

import android.app.Application
import androidx.lifecycle.*
import com.example.dosecerta.data.model.Medication
import com.example.dosecerta.data.repository.MedicationRepository
import com.example.dosecerta.util.ReminderScheduler
import kotlinx.coroutines.launch

class MedsViewModel(
    application: Application,
    private val repository: MedicationRepository
) : AndroidViewModel(application) {

    // LiveData holding the list of all medications
    val allMedications: LiveData<List<Medication>> = repository.allMedications

    // Function to delete a medication
    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            // Get reminders associated with this medication BEFORE deleting it
            val remindersToDelete = repository.getRemindersForMedicationSync(medication.id)
            // Cancel associated alarms
            if (remindersToDelete.isNotEmpty()) {
                ReminderScheduler.cancelReminders(getApplication(), remindersToDelete)
            }
            // Now delete the medication (CASCADE should handle deleting reminders/logs from DB)
            repository.deleteMedication(medication)
        }
    }

    // Add functions for adding/editing medications later, which will likely navigate
    // to a different screen/fragment.
} 