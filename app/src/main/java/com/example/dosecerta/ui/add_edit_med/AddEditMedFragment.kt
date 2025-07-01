package com.example.dosecerta.ui.add_edit_med

import com.example.dosecerta.R

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter



import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
 
import androidx.navigation.fragment.findNavController
 
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
 

import com.example.dosecerta.data.model.DosageForm
import com.example.dosecerta.data.model.FrequencyType
import com.example.dosecerta.data.model.Medication
import com.example.dosecerta.data.model.StrengthUnit
import com.example.dosecerta.databinding.FragmentAddEditMedBinding
import com.example.dosecerta.util.EnumHelper
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
 
import java.util.Calendar
import androidx.core.content.ContextCompat

class AddEditMedFragment : Fragment() {

    private var _binding: FragmentAddEditMedBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditMedViewModel by viewModels { AddEditMedViewModelFactory }
 
    private lateinit var reminderAdapter: ReminderAdapter

    // Launcher for the exact alarm permission setting
    private lateinit var requestExactAlarmPermissionLauncher: ActivityResultLauncher<Intent>
    // Launcher for the notification permission
    private lateinit var requestNotificationPermissionLauncher: ActivityResultLauncher<String>

    // Map Chip IDs to Calendar.DAY_OF_WEEK constants
    private val dayChipMap: Map<Int, Int> by lazy {
        mapOf(
            binding.chipSun.id to Calendar.SUNDAY,
            binding.chipMon.id to Calendar.MONDAY,
            binding.chipTue.id to Calendar.TUESDAY,
            binding.chipWed.id to Calendar.WEDNESDAY,
            binding.chipThu.id to Calendar.THURSDAY,
            binding.chipFri.id to Calendar.FRIDAY,
            binding.chipSat.id to Calendar.SATURDAY
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
        super.onCreate(savedInstanceState)

        // Initialize the ActivityResultLauncher for notifications
        requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("AddEditMedFragment", "POST_NOTIFICATIONS permission granted.")
                // Notification permission granted, now check/request exact alarm permission
                checkAndRequestExactAlarmPermission()
            } else {
                Log.w("AddEditMedFragment", "POST_NOTIFICATIONS permission denied.")
                Snackbar.make(binding.root, "Notifications permission is needed to show medication reminders.", Snackbar.LENGTH_LONG).show()
                // Don't proceed with saving if notifications are essential for reminders
            }
        }

        // Initialize the ActivityResultLauncher for exact alarms
        requestExactAlarmPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Check permission again after returning from settings
            if (canScheduleExactAlarms()) {
                // Permission granted, proceed with saving the data
                // We store the data temporarily when save is first clicked if permission is needed
                Log.d("AddEditMedFragment", "Exact alarm permission granted after returning from settings.")
                triggerSaveMedication()
            } else {
                // Permission still not granted
                Log.w("AddEditMedFragment", "Exact alarm permission was not granted.")
                Snackbar.make(binding.root, "Exact alarm permission is needed to schedule reminders accurately.", Snackbar.LENGTH_LONG).show()
            }
            }
        } catch (e: Exception) {
            Log.e("AddEditMedFragment", "Exception in onCreate", e)
            throw e
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
        _binding = FragmentAddEditMedBinding.inflate(inflater, container, false)

        setupToolbar()
        setupSpinners() // Includes frequency spinner now
        setupFrequencySelection() // Logic for showing/hiding frequency details
        setupReminderList()
        setupAddReminderButton()
        setupSaveButton()
        observeViewModel()

        // Set title based on mode (ViewModel determines if it loads data)
        if (viewModel.isEditMode) {
             binding.toolbar.title = getString(R.string.edit_medication)
        } else {
             binding.toolbar.title = getString(R.string.add_medication)
        }

            binding.root
        } catch (e: Exception) {
            Log.e("AddEditMedFragment", "Exception in onCreateView", e)
            throw e
        }
    }

    // Helper function to check notification permission
    private fun hasNotificationPermission(): Boolean {
        // No runtime permission needed before Android 13 (API 33)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Helper function to check exact alarm permission
    private fun canScheduleExactAlarms(): Boolean {
        // Always true for versions below Android 12 (API 31)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return alarmManager?.canScheduleExactAlarms() ?: false
    }

    // Function to request exact alarm permission
    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                // Optional: specify package if needed, but usually not necessary
                // data = Uri.parse("package:${requireContext().packageName}")
            }
            // Show a Snackbar explaining why the permission is needed before launching settings
            Snackbar.make(binding.root, "This app needs permission to schedule exact alarms for medication reminders.", Snackbar.LENGTH_LONG)
                .setAction("Settings") { // Add an action to launch the settings intent
                    requestExactAlarmPermissionLauncher.launch(intent)
                 }
                .show()
        }
    }

    // Function to request Notification permission (Android 13+)
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                 // Explain why the permission is needed (e.g., via a dialog or Snackbar)
                 Snackbar.make(binding.root, "Notifications are used to show medication reminders.", Snackbar.LENGTH_INDEFINITE)
                     .setAction("OK") { 
                          requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                     }
                     .show()
            } else {
                // Directly request the permission
                 requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupSpinners() {
        // Dosage Form
        val dosageForms = EnumHelper.getAllDosageFormsDisplayNames(requireContext())
        val dosageFormAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, dosageForms)
        (binding.spinnerMedDosageForm).setAdapter(dosageFormAdapter)

        // Strength Unit
        val strengthUnits = StrengthUnit.entries.map { it.name }
        val strengthUnitAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, strengthUnits)
        (binding.spinnerMedStrengthUnit).setAdapter(strengthUnitAdapter)

        // Frequency Type
        val frequencyTypes = EnumHelper.getAllFrequencyTypesDisplayNames(requireContext())
        val frequencyTypeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, frequencyTypes)
        (binding.spinnerFrequencyType).setAdapter(frequencyTypeAdapter)

        // Set default selection if needed (though ViewModel handles population)
        // binding.spinnerFrequencyType.setText(EnumHelper.formatFrequencyType(requireContext(), FrequencyType.DAILY), false)
    }

    private fun setupFrequencySelection() {
        binding.spinnerFrequencyType.setOnItemClickListener { _, _, position, _ ->
            val selectedType = FrequencyType.entries[position]
            updateFrequencyVisibility(selectedType)
        }

        // Initialize visibility based on default (likely DAILY initially)
        updateFrequencyVisibility(FrequencyType.DAILY)
    }

    private fun updateFrequencyVisibility(type: FrequencyType) {
        binding.layoutFrequencyInterval.visibility = if (type == FrequencyType.EVERY_X_DAYS) View.VISIBLE else View.GONE
        binding.labelFrequencyDays.visibility = if (type == FrequencyType.SPECIFIC_DAYS_OF_WEEK) View.VISIBLE else View.GONE
        binding.chipGroupFrequencyDays.visibility = if (type == FrequencyType.SPECIFIC_DAYS_OF_WEEK) View.VISIBLE else View.GONE
    }



    private fun setupReminderList() {
        reminderAdapter = ReminderAdapter {
            reminderItem -> 
            // Confirm before deleting reminder locally
             viewModel.removeReminder(reminderItem)
        }
        binding.remindersRecyclerView.adapter = reminderAdapter
        binding.remindersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupAddReminderButton() {
        binding.buttonAddReminder.setOnClickListener {
            showTimePicker()
        }
    }
    
    private fun showTimePicker() {
        val currentTime = Calendar.getInstance()
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
        val minute = currentTime.get(Calendar.MINUTE)

        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H) // Or CLOCK_24H
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(getString(R.string.add_time))
            .build()

        timePicker.addOnPositiveButtonClickListener { 
             viewModel.addReminder(timePicker.hour, timePicker.minute)
        }

        timePicker.show(childFragmentManager, "TIME_PICKER_TAG")
    }

    private fun setupSaveButton() {
        binding.fabSaveMedication.setOnClickListener {
            saveMedicationData()
        }
    }

    private fun observeViewModel() {
        viewModel.medication.observe(viewLifecycleOwner) { medication ->
            medication?.let { populateFields(it) }
        }

        viewModel.navigateBack.observe(viewLifecycleOwner) { navigate ->
            if (navigate) {
                findNavController().navigateUp()
                viewModel.onNavigationComplete()
            }
        }

        viewModel.validationErrors.observe(viewLifecycleOwner) { errors ->
            clearErrors()
            if (errors.isNotEmpty()) {
                errors.forEach { (viewIdentifier, message) ->
                    when(viewIdentifier) { 
                        1 -> binding.layoutMedName.error = message
                        2 -> binding.layoutMedDosage.error = message
                        3 -> binding.layoutMedDosageForm.error = message
                        5 -> binding.layoutMedStrengthUnit.error = message
                        6 -> binding.layoutMedStrength.error = message
                        7 -> binding.layoutFrequencyType.error = message
                        8 -> binding.layoutFrequencyInterval.error = message
                        9 -> {
                             Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                             binding.labelFrequencyDays.setTextColor(ContextCompat.getColor(requireContext(), com.google.android.material.R.color.design_default_color_error))
                        }
                        else -> { /* Unknown error ID */ }
                    }
                }
                 Snackbar.make(binding.root, getString(R.string.check_fields_above), Snackbar.LENGTH_SHORT).show()
            }
        }
        
        viewModel.reminders.observe(viewLifecycleOwner) { reminderList ->
            reminderAdapter.submitList(reminderList)
            binding.textNoReminders.visibility = if (reminderList.isNullOrEmpty()) View.VISIBLE else View.GONE
            binding.remindersRecyclerView.visibility = if (reminderList.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun populateFields(medication: Medication) {
         binding.editMedName.setText(medication.name)
         binding.editMedDosage.setText(medication.dosage)
         binding.editMedStrength.setText(medication.strength)
         binding.editMedNotes.setText(medication.notes)

         val dosageFormString = EnumHelper.formatDosageForm(requireContext(), medication.dosageForm)
         (binding.spinnerMedDosageForm).setText(dosageFormString, false)

         medication.strengthUnit?.let {
            val strengthUnitString = it.name
            (binding.spinnerMedStrengthUnit).setText(strengthUnitString, false)
         }
         
         // Populate Frequency
         val frequencyTypeString = EnumHelper.formatFrequencyType(requireContext(), medication.frequencyType)
         (binding.spinnerFrequencyType).setText(frequencyTypeString, false)
         updateFrequencyVisibility(medication.frequencyType)

         if (medication.frequencyType == FrequencyType.EVERY_X_DAYS) {
             binding.editFrequencyInterval.setText(medication.frequencyIntervalDays?.toString() ?: "")
         }
         
         if (medication.frequencyType == FrequencyType.SPECIFIC_DAYS_OF_WEEK) {
            val selectedDays = medication.frequencyDaysOfWeek?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            dayChipMap.forEach { (chipId, dayConstant) ->
                (binding.chipGroupFrequencyDays.findViewById<Chip>(chipId))?.isChecked = dayConstant in selectedDays
            }
         }

         // ViewModel loads existing reminders separately via loadMedication
    }

    private fun saveMedicationData() {
        // Step 1: Check/Request Notification Permission (Android 13+)
        if (!hasNotificationPermission()) {
            Log.i("AddEditMedFragment", "POST_NOTIFICATIONS permission required. Requesting...")
            requestNotificationPermission()
            // Stop here; the launcher callback will continue the process if granted
            return
        }

        Log.i("AddEditMedFragment", "POST_NOTIFICATIONS permission granted or not required.")
        // Step 2: Check/Request Exact Alarm Permission (Android 12+)
        checkAndRequestExactAlarmPermission()
    }

    // Helper function to consolidate the exact alarm check/request logic
    private fun checkAndRequestExactAlarmPermission() {
        if (!canScheduleExactAlarms()) {
            Log.i("AddEditMedFragment", "Exact alarm permission required and not granted. Requesting...")
            requestExactAlarmPermission()
            // Stop here; the launcher callback will continue the process if granted
            return
        }

        // Both permissions granted or not required, proceed with saving
        Log.i("AddEditMedFragment", "All necessary permissions granted or not required. Proceeding with save.")
        triggerSaveMedication()
    }

    // This function contains the actual logic to gather data and call the ViewModel
    // It's separated so it can be called either directly or from the permission launcher callback
    private fun triggerSaveMedication() {
        val name = binding.editMedName.text.toString()
        val dosage = binding.editMedDosage.text.toString()
        val strength = binding.editMedStrength.text.toString()
        val notes = binding.editMedNotes.text.toString()

        val dosageFormString = binding.spinnerMedDosageForm.text.toString()
        val dosageForm = EnumHelper.getDosageFormByDisplayName(requireContext(), dosageFormString)

        val strengthUnitString = binding.spinnerMedStrengthUnit.text.toString()
        val strengthUnit = StrengthUnit.entries.find { it.name.equals(strengthUnitString, ignoreCase = true) }

        // Frequency Data
        val frequencyTypeString = binding.spinnerFrequencyType.text.toString()
        val frequencyType = EnumHelper.getFrequencyTypeByDisplayName(requireContext(), frequencyTypeString)
        var frequencyInterval: Int? = null
        var frequencyDays: String? = null

        if (frequencyType == FrequencyType.EVERY_X_DAYS) {
            val intervalString = binding.editFrequencyInterval.text.toString()
            frequencyInterval = intervalString.toIntOrNull()

            if (frequencyInterval == null) {
                binding.layoutFrequencyInterval.error = getString(R.string.error_enter_interval)
                return
            }
        }
        if (frequencyType == FrequencyType.SPECIFIC_DAYS_OF_WEEK) {
            frequencyDays = dayChipMap.filterKeys { binding.chipGroupFrequencyDays.findViewById<Chip>(it)?.isChecked == true }
                .values
                .sorted()
                .joinToString(",")
        }

        clearErrors()

        val selectedDosageForm = dosageForm ?: run {
             binding.layoutMedDosageForm.error = getString(R.string.error_select_dosage_form)
             return 
        } 
        
        val selectedFrequencyType = frequencyType ?: run {
             binding.layoutFrequencyType.error = getString(R.string.error_select_frequency)
             return
        }

        viewModel.saveMedication(
            name = name,
            dosage = dosage,
            dosageForm = selectedDosageForm,
            strength = strength,
            strengthUnit = strengthUnit,
            notes = notes,
            frequencyType = selectedFrequencyType,
            frequencyIntervalDays = frequencyInterval,
            frequencyDaysOfWeek = frequencyDays
        )
    }



    @SuppressLint("PrivateResource")
    private fun clearErrors() {
        binding.layoutMedName.error = null
        binding.layoutMedDosage.error = null
        binding.layoutMedDosageForm.error = null
        binding.layoutMedStrength.error = null
        binding.layoutMedStrengthUnit.error = null
        binding.layoutFrequencyType.error = null
        binding.layoutFrequencyInterval.error = null
        binding.labelFrequencyDays.setTextColor(ContextCompat.getColor(requireContext(), com.google.android.material.R.color.design_default_color_on_surface))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.remindersRecyclerView.adapter = null
        _binding = null
    }
} 