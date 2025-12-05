package com.dosecerta.ui.addmedication

import android.app.AlertDialog
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
        val alarmScheduler = com.dosecerta.alarm.AlarmScheduler(requireContext())
        val medicationId = arguments?.getLong("medicationId", -1L) ?: -1L
        AddMedicationViewModelFactory(repository, alarmScheduler, medicationId)
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
        observeFormData()
    }
    
    private fun setupUI() {
        // Top Bar Buttons
        binding.buttonCancel.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.buttonSave.setOnClickListener {
            saveMedication()
        }
        
        // Unit Dropdown
        val units = listOf("mg", "ml", "cp", "gts", "g", "mcg", "UI", "amp", "env", "L", "oz")
        val unitAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            units
        )
        binding.autoCompleteUnit.setAdapter(unitAdapter)
        binding.autoCompleteUnit.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateUnit(units[position])
        }
        
        // Form Dropdown
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
        
        // Color picker
        setupColorPicker()
        
        // Add time button
        binding.buttonAddTime.setOnClickListener {
            showTimePicker()
        }
    }
    
    private fun saveMedication() {
        viewModel.updateName(binding.editName.text.toString())
        viewModel.updateDosage(binding.editDosage.text.toString())
        // Unit is updated via selection or text change if user types custom
        if (binding.autoCompleteUnit.text.toString().isNotEmpty()) {
            viewModel.updateUnit(binding.autoCompleteUnit.text.toString())
        }
        viewModel.updateNotes(binding.editNotes.text.toString())
        viewModel.saveMedication()
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
                        showSuccessDialog()
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
    
    private fun showSuccessDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.success)
            .setMessage(R.string.medication_saved)
            .setIcon(R.drawable.ic_check)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                findNavController().navigateUp()
            }
            .setCancelable(false)
            .show()
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
    
    private fun getFrequencyName(frequency: Frequency): String {
        return when (frequency) {
            Frequency.DAILY -> getString(R.string.frequency_daily)
            Frequency.WEEKLY -> getString(R.string.frequency_weekly)
            Frequency.MONTHLY -> getString(R.string.frequency_monthly)
            Frequency.AS_NEEDED -> getString(R.string.frequency_as_needed)
            Frequency.CUSTOM -> getString(R.string.frequency_custom)
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

    private fun observeFormData() {
        // Observe and populate form fields when medication data is loaded (for edit mode)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.medicationName.collect { name ->
                if (name.isNotEmpty() && binding.editName.text.toString() != name) {
                    binding.editName.setText(name)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.dosage.collect { dosage ->
                if (dosage.isNotEmpty() && binding.editDosage.text.toString() != dosage) {
                    binding.editDosage.setText(dosage)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unit.collect { unit ->
                if (binding.autoCompleteUnit.text.toString() != unit) {
                    binding.autoCompleteUnit.setText(unit, false)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.form.collect { form ->
                binding.autoCompleteForm.setText(getFormName(form), false)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.frequency.collect { frequency ->
                binding.autoCompleteFrequency.setText(getFrequencyName(frequency), false)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notes.collect { notes ->
                if (binding.editNotes.text.toString() != notes) {
                    binding.editNotes.setText(notes)
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.color.collect { color ->
                updateColorSelection(color)
            }
        }
    }
    
    private fun setupColorPicker() {
        val colors = mapOf(
            binding.colorOptionTeal to 0xFF00897B.toInt(),
            binding.colorOptionBlue to 0xFF1976D2.toInt(),
            binding.colorOptionPurple to 0xFF7B1FA2.toInt(),
            binding.colorOptionRed to 0xFFD32F2F.toInt(),
            binding.colorOptionOrange to 0xFFF57C00.toInt(),
            binding.colorOptionGreen to 0xFF388E3C.toInt()
        )
        
        colors.forEach { (imageView, colorValue) ->
            imageView.setOnClickListener {
                viewModel.updateColor(colorValue)
            }
        }
    }
    
    private fun updateColorSelection(selectedColor: Int) {
        val colors = mapOf(
            binding.colorOptionTeal to 0xFF00897B.toInt(),
            binding.colorOptionBlue to 0xFF1976D2.toInt(),
            binding.colorOptionPurple to 0xFF7B1FA2.toInt(),
            binding.colorOptionRed to 0xFFD32F2F.toInt(),
            binding.colorOptionOrange to 0xFFF57C00.toInt(),
            binding.colorOptionGreen to 0xFF388E3C.toInt()
        )
        
        colors.forEach { (imageView, colorValue) ->
            if (colorValue == selectedColor) {
                imageView.alpha = 1.0f
                imageView.scaleX = 1.0f
                imageView.scaleY = 1.0f
            } else {
                imageView.alpha = 0.5f
                imageView.scaleX = 0.8f
                imageView.scaleY = 0.8f
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class AddMedicationViewModelFactory(
    private val repository: MedicationRepository,
    private val alarmScheduler: com.dosecerta.alarm.AlarmScheduler,
    private val medicationId: Long = -1L
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddMedicationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddMedicationViewModel(repository, alarmScheduler, medicationId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
