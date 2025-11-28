package com.dosecerta.ui.addmedication

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.FragmentAddMedicationBinding
import kotlinx.coroutines.launch
import java.util.*

/**
 * Fragment for adding a new medication.
 */
class AddMedicationFragment : Fragment() {
    
    private var _binding: FragmentAddMedicationBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AddMedicationViewModel by viewModels {
        val database = DoseCertaDatabase.getDatabase(requireContext())
        val repository = MedicationRepository(
            database.medicationDao(),
            database.scheduleDao(),
            database.medicationLogDao()
        )
        val medicationId = arguments?.getLong("medicationId", -1L) ?: -1L
        AddMedicationViewModelFactory(repository, medicationId)
    }
    
    private lateinit var timeAdapter: ScheduleTimeAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddMedicationBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupRecyclerView()
        observeViewModel()
    }
    
    private fun setupUI() {
        // Form dropdown
        val formAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            PharmaceuticalForm.values().map { getFormName(it) }
        )
        binding.autoCompleteForm.setAdapter(formAdapter)
        binding.autoCompleteForm.setText(getFormName(PharmaceuticalForm.TABLET), false)
        binding.autoCompleteForm.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateForm(PharmaceuticalForm.values()[position])
        }
        
        // Frequency dropdown
        val frequencyAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            Frequency.values().map { getFrequencyName(it) }
        )
        binding.autoCompleteFrequency.setAdapter(frequencyAdapter)
        binding.autoCompleteFrequency.setText(getFrequencyName(Frequency.DAILY), false)
        binding.autoCompleteFrequency.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateFrequency(Frequency.values()[position])
        }
        
        // Add time button
        binding.buttonAddTime.setOnClickListener {
            showTimePicker()
        }
        
        // Save button
        binding.buttonSave.setOnClickListener {
            viewModel.updateName(binding.editName.text.toString())
            viewModel.updateDosage(binding.editDosage.text.toString())
            viewModel.updateUnit(binding.editUnit.text.toString())
            viewModel.updateNotes(binding.editNotes.text.toString())
            viewModel.saveMedication()
        }
    }
    
    private fun setupRecyclerView() {
        timeAdapter = ScheduleTimeAdapter(
            onDeleteClick = { timeInMinutes ->
                viewModel.removeScheduleTime(timeInMinutes)
            }
        )
        
        binding.recyclerTimes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTimes.adapter = timeAdapter
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scheduleTimes.collect { times ->
                timeAdapter.submitList(times)
                updateEmptyState(times.isEmpty())
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveState.collect { state ->
                when (state) {
                    is AddMedicationViewModel.SaveState.Idle -> {
                        binding.buttonSave.isEnabled = true
                    }
                    is AddMedicationViewModel.SaveState.Saving -> {
                        binding.buttonSave.isEnabled = false
                    }
                    is AddMedicationViewModel.SaveState.Success -> {
                        Toast.makeText(requireContext(), R.string.medication_saved, Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    is AddMedicationViewModel.SaveState.Error -> {
                        binding.buttonSave.isEnabled = true
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetSaveState()
                    }
                }
            }
        }
    }
    
    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val timeInMinutes = hourOfDay * 60 + minute
                viewModel.addScheduleTime(timeInMinutes)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.recyclerTimes.visibility = View.GONE
            binding.textNoReminders.visibility = View.VISIBLE
        } else {
            binding.recyclerTimes.visibility = View.VISIBLE
            binding.textNoReminders.visibility = View.GONE
        }
    }
    
    private fun getFormName(form: PharmaceuticalForm): String {
        return when (form) {
            PharmaceuticalForm.TABLET -> getString(R.string.form_tablet)
            PharmaceuticalForm.CAPSULE -> getString(R.string.form_capsule)
            PharmaceuticalForm.SYRUP -> getString(R.string.form_syrup)
            PharmaceuticalForm.DROPS -> getString(R.string.form_drops)
            PharmaceuticalForm.INJECTION -> getString(R.string.form_injection)
            PharmaceuticalForm.CREAM -> getString(R.string.form_cream)
            PharmaceuticalForm.SPRAY -> getString(R.string.form_spray)
            PharmaceuticalForm.OTHER -> getString(R.string.form_other)
        }
    }
    
    private fun getFrequencyName(frequency: Frequency): String {
        return when (frequency) {
            Frequency.DAILY -> getString(R.string.frequency_daily)
            Frequency.WEEKLY -> getString(R.string.frequency_weekly)
            Frequency.MONTHLY -> getString(R.string.frequency_monthly)
            Frequency.AS_NEEDED -> getString(R.string.frequency_as_needed)
            Frequency.CUSTOM -> getString(R.string.frequency_custom)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class AddMedicationViewModelFactory(
    private val repository: MedicationRepository,
    private val medicationId: Long = -1L
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddMedicationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddMedicationViewModel(repository, medicationId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
