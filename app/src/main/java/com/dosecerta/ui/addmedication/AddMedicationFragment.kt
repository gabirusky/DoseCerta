package com.dosecerta.ui.addmedication

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import com.dosecerta.data.model.ScheduleTime
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.FragmentAddMedicationBinding
import com.dosecerta.ui.UiLabels
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

class AddMedicationFragment : Fragment() {
    private var _binding: FragmentAddMedicationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AddMedicationViewModel by viewModels {
        val db = DoseCertaDatabase.getDatabase(requireContext())
        AddMedicationViewModelFactory(MedicationRepository(db.medicationDao(), db.scheduleDao(), db.medicationLogDao()),
            com.dosecerta.alarm.AlarmScheduler(requireContext()), arguments?.getLong("medicationId", -1L) ?: -1L)
    }
    private lateinit var timeAdapter: ScheduleTimeAdapter
    private val dayChips = mutableMapOf<Int, Chip>()
    private var successVisible = false
    private var applyingDays = false
    private var detailsExpanded = false
    private var previewExpanded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentAddMedicationBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        binding.toolbar.setTitle(if (viewModel.isEditMode) R.string.medication_edit_title else R.string.medication_add_title)
        binding.toolbar.setNavigationOnClickListener { confirmLeave() }
        detailsExpanded = state?.getBoolean("details_expanded") ?: false
        previewExpanded = state?.getBoolean("preview_expanded") ?: false
        renderDetails()
        renderPreviewVisibility()
        binding.buttonDetails.setOnClickListener { detailsExpanded = !detailsExpanded; renderDetails() }
        binding.buttonPreview.setOnClickListener { previewExpanded = !previewExpanded; renderPreviewVisibility() }
        // Keep the compact dose/unit row legible with large fonts or narrow windows.
        if (resources.configuration.fontScale > 1.3f || resources.configuration.screenWidthDp < 360) {
            binding.doseRow.orientation = android.widget.LinearLayout.VERTICAL
            listOf(binding.inputDosage, binding.inputUnit).forEach { input ->
                input.layoutParams = android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            }
        }
        binding.buttonCancel.setOnClickListener { confirmLeave() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmLeave()
        })
        binding.buttonSave.setOnClickListener { if (viewModel.saveState.value is AddMedicationViewModel.SaveState.LoadError) viewModel.loadMedication() else viewModel.saveMedication() }
        binding.editName.doAfterTextChanged { if (it.toString() != viewModel.medicationName.value) viewModel.updateName(it.toString()) }
        binding.editDosage.doAfterTextChanged { if (it.toString() != viewModel.dosage.value) viewModel.updateDosage(it.toString()) }
        binding.autoCompleteUnit.doAfterTextChanged { if (it.toString() != viewModel.unit.value) viewModel.updateUnit(it.toString()) }
        binding.editNotes.doAfterTextChanged { if (it.toString() != viewModel.notes.value) viewModel.updateNotes(it.toString()) }
        binding.editMonthDay.doAfterTextChanged { if (it.toString() != viewModel.monthDay.value) viewModel.updateMonthDay(it.toString()) }
        binding.autoCompleteUnit.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line,
            listOf("mg", "ml", "cp", "gts", "g", "mcg", "UI", "amp", "env", "L", "oz")))
        val forms = PharmaceuticalForm.values()
        binding.autoCompleteForm.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, forms.map { UiLabels.form(requireContext(), it) }))
        binding.autoCompleteForm.setOnItemClickListener { parent, _, index, _ ->
            val selected = if (index >= 0) parent?.getItemAtPosition(index)?.toString() else null
            forms.firstOrNull { UiLabels.form(requireContext(), it) == (selected ?: binding.autoCompleteForm.text.toString()) }
                ?.let(viewModel::updateForm)
        }
        val frequencies = Frequency.values()
        binding.autoCompleteFrequency.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, frequencies.map { UiLabels.frequency(requireContext(), it) }))
        binding.autoCompleteFrequency.setOnItemClickListener { parent, _, index, _ ->
            // The dropdown may filter or reorder its items; its position is not an enum ordinal.
            val selected = if (index >= 0) parent?.getItemAtPosition(index)?.toString() else null
            frequencies.firstOrNull { UiLabels.frequency(requireContext(), it) == (selected ?: binding.autoCompleteFrequency.text.toString()) }
                ?.let(viewModel::updateFrequency)
        }
        val dayNames = java.text.DateFormatSymbols.getInstance().weekdays
        (1..7).forEach { day ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId(); text = dayNames[day]; isCheckable = true
                ensureAccessibleTouchTarget((48 * resources.displayMetrics.density).toInt())
                setOnCheckedChangeListener { _, checked -> if (!applyingDays) {
                    viewModel.updateDays(if (checked) viewModel.days.value + day else viewModel.days.value - day)
                } }
            }
            dayChips[day] = chip; binding.chipDays.addView(chip)
        }
        timeAdapter = ScheduleTimeAdapter(onEdit = ::pickTime, onRemove = viewModel::removeScheduleTime)
        binding.recyclerTimes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerTimes.adapter = timeAdapter
        binding.buttonAddTime.setOnClickListener { pickTime(null) }
        binding.buttonGenerateTimes.setOnClickListener { viewModel.generateDefaultReminders(viewModel.frequency.value) }
        colorChips().forEach { (chip, color) ->
            chip.chipIcon = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_circle)
            chip.chipIconTint = android.content.res.ColorStateList.valueOf(color)
            chip.isChipIconVisible = true
            chip.setOnClickListener { viewModel.updateColor(color) }
        }
        observe()
    }
    private fun pickTime(existing: ScheduleTime?) {
        val now = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, hour, minute ->
            val minutes = hour * 60 + minute
            val accepted = if (existing == null) viewModel.addScheduleTime(minutes) else viewModel.editScheduleTime(existing, minutes)
            if (!accepted) Toast.makeText(requireContext(), R.string.ui_duplicate_time, Toast.LENGTH_SHORT).show()
        }, existing?.timeInMinutes?.div(60) ?: now.get(Calendar.HOUR_OF_DAY),
            existing?.timeInMinutes?.rem(60) ?: now.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(requireContext()))
            .apply { setTitle(if (existing == null) R.string.medication_add_time else R.string.ui_edit_reminder_time) }.show()
    }
    private fun renderDetails() {
        binding.detailsContainer.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
        binding.buttonDetails.setText(if (detailsExpanded) R.string.ui_hide_details else R.string.ui_more_details)
        ViewCompat.setStateDescription(binding.buttonDetails, getString(if (detailsExpanded) R.string.ui_expanded else R.string.ui_collapsed))
    }
    private fun renderPreviewVisibility() {
        binding.textPreview.visibility = if (previewExpanded) View.VISIBLE else View.GONE
        binding.buttonPreview.setText(if (previewExpanded) R.string.ui_hide_next_dates else R.string.ui_next_dates)
        ViewCompat.setStateDescription(binding.buttonPreview, getString(if (previewExpanded) R.string.ui_expanded else R.string.ui_collapsed))
    }
    private fun colorChips() = mapOf(binding.colorOptionTeal to 0xFF00897B.toInt(), binding.colorOptionBlue to 0xFF1976D2.toInt(),
        binding.colorOptionPurple to 0xFF7B1FA2.toInt(), binding.colorOptionRed to 0xFFD32F2F.toInt(),
        binding.colorOptionOrange to 0xFFF57C00.toInt(), binding.colorOptionGreen to 0xFF388E3C.toInt())
    private fun observe() = viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.medicationName.collect { if (binding.editName.text.toString() != it) binding.editName.setText(it) } }
            launch { viewModel.dosage.collect { if (binding.editDosage.text.toString() != it) binding.editDosage.setText(it) } }
            launch { viewModel.unit.collect { if (binding.autoCompleteUnit.text.toString() != it) binding.autoCompleteUnit.setText(it, false) } }
            launch { viewModel.notes.collect { if (binding.editNotes.text.toString() != it) binding.editNotes.setText(it) } }
            launch { viewModel.form.collect { binding.autoCompleteForm.setText(UiLabels.form(requireContext(), it), false) } }
            launch { viewModel.color.collect { selected -> colorChips().forEach { (chip, color) ->
                chip.isChecked = color == selected
                ViewCompat.setStateDescription(chip, getString(if (chip.isChecked) R.string.ui_selected else R.string.ui_not_selected))
            } } }
            launch { viewModel.frequency.collect { frequency ->
                binding.autoCompleteFrequency.setText(UiLabels.frequency(requireContext(), frequency), false)
                val prn = frequency == Frequency.AS_NEEDED
                binding.remindersSectionContainer.visibility = if (prn) View.GONE else View.VISIBLE
                binding.textAsNeededHint.visibility = if (prn) View.VISIBLE else View.GONE
                binding.daysContainer.visibility = if (frequency in listOf(Frequency.WEEKLY, Frequency.SELECTED_DAYS)) View.VISIBLE else View.GONE
                binding.monthContainer.visibility = if (frequency == Frequency.MONTHLY) View.VISIBLE else View.GONE
                binding.buttonGenerateTimes.visibility = if (frequency.intervalHours in 1..12) View.VISIBLE else View.GONE
                binding.textIntervalHint.visibility = if (frequency.intervalHours in 1..12) View.VISIBLE else View.GONE
                binding.previewContainer.visibility = if (prn) View.GONE else View.VISIBLE
            } }
            launch { viewModel.monthDay.collect { if (binding.editMonthDay.text.toString() != it) binding.editMonthDay.setText(it) } }
            launch { viewModel.days.collect { days ->
                applyingDays = true
                dayChips.forEach { (day, chip) -> chip.isChecked = day in days; ViewCompat.setStateDescription(chip, getString(if (chip.isChecked) R.string.ui_selected else R.string.ui_not_selected)) }
                applyingDays = false
            } }
            launch { viewModel.scheduleTimes.collect { times -> timeAdapter.submitList(times); binding.textNoReminders.visibility = if (times.isEmpty()) View.VISIBLE else View.GONE } }
            launch { combine(viewModel.frequency, viewModel.days, viewModel.monthDay, viewModel.scheduleTimes) { _, _, _, _ -> viewModel.preview() }.collect { dates ->
                val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, resources.configuration.locales[0])
                binding.textPreview.text = if (dates.isEmpty()) getString(if (viewModel.frequency.value == Frequency.AS_NEEDED) R.string.ui_as_needed_preview else R.string.ui_preview_empty)
                    else dates.joinToString("\n") { date -> format.timeZone = java.util.TimeZone.getTimeZone(date.zoneId); format.format(Date(date.originalDueAt)) + if (date.adjustedForDst) getString(R.string.ui_dst_adjusted) else "" }
                binding.textNextReminder.text = dates.firstOrNull()?.let { date ->
                    format.timeZone = java.util.TimeZone.getTimeZone(date.zoneId)
                    getString(R.string.ui_next_reminder, format.format(Date(date.originalDueAt)))
                } ?: getString(R.string.ui_preview_empty)
                binding.buttonPreview.visibility = if (dates.isEmpty()) View.GONE else View.VISIBLE
            } }
            launch { viewModel.errors.collect { errors ->
                fun error(field: AddMedicationViewModel.Field, res: Int) = if (field in errors) getString(res) else null
                binding.inputName.error = error(AddMedicationViewModel.Field.NAME, R.string.ui_name_required)
                binding.inputDosage.error = error(AddMedicationViewModel.Field.DOSAGE, R.string.ui_dosage_required)
                binding.inputUnit.error = error(AddMedicationViewModel.Field.UNIT, R.string.ui_unit_required)
                binding.inputMonthDay.error = error(AddMedicationViewModel.Field.MONTH_DAY, R.string.ui_month_day_error)
                binding.textDaysError.visibility = if (AddMedicationViewModel.Field.DAYS in errors) View.VISIBLE else View.GONE
                binding.textTimesError.visibility = if (AddMedicationViewModel.Field.TIMES in errors) View.VISIBLE else View.GONE
                if (errors.isNotEmpty()) {
                    val focus = when { AddMedicationViewModel.Field.NAME in errors -> binding.editName
                        AddMedicationViewModel.Field.DOSAGE in errors -> binding.editDosage
                        AddMedicationViewModel.Field.UNIT in errors -> binding.autoCompleteUnit
                        AddMedicationViewModel.Field.DAYS in errors -> binding.chipDays
                        AddMedicationViewModel.Field.MONTH_DAY in errors -> binding.editMonthDay
                        else -> binding.buttonAddTime }
                    focus.requestFocus()
                }
            } }
            launch { combine(viewModel.saveState, viewModel.loaded) { save, loaded -> save to loaded }.collect { (state, loaded) ->
                binding.buttonSave.isEnabled = state is AddMedicationViewModel.SaveState.LoadError || (loaded && (state is AddMedicationViewModel.SaveState.Idle || state is AddMedicationViewModel.SaveState.Error))
                binding.buttonCancel.isEnabled = state !is AddMedicationViewModel.SaveState.Saving
                binding.buttonSave.setText(when (state) { is AddMedicationViewModel.SaveState.Saving -> R.string.ui_saving; is AddMedicationViewModel.SaveState.LoadError -> R.string.ui_reload; else -> R.string.ui_save })
                binding.textSaveError.setText(if (state is AddMedicationViewModel.SaveState.LoadError) R.string.ui_load_error else R.string.ui_error_save)
                binding.textSaveError.visibility = if (state is AddMedicationViewModel.SaveState.Error || state is AddMedicationViewModel.SaveState.LoadError) View.VISIBLE else View.GONE
                if (state is AddMedicationViewModel.SaveState.Success && !successVisible) {
                    successVisible = true
                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.success).setMessage(getString(R.string.medication_saved) + if (viewModel.reminderWarning.value) "\n\n" + getString(R.string.ui_saved_reminder_warning) else "")
                        .setPositiveButton(R.string.ok) { _, _ -> findNavController().navigateUp() }.setCancelable(false).show()
                }
            } }
        }
    }
    private fun confirmLeave() {
        if (viewModel.saveState.value is AddMedicationViewModel.SaveState.Saving) return
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ui_draft_discard_title).setMessage(R.string.ui_draft_discard_hint)
            .setNegativeButton(R.string.ui_stay, null).setPositiveButton(R.string.ui_leave) { _, _ -> findNavController().navigateUp() }.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("details_expanded", detailsExpanded)
        outState.putBoolean("preview_expanded", previewExpanded)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() { successVisible = false; dayChips.clear(); _binding = null; super.onDestroyView() }
}

class AddMedicationViewModelFactory(private val repository: MedicationRepository, private val alarmScheduler: com.dosecerta.alarm.AlarmScheduler,
    private val medicationId: Long = -1L) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return AddMedicationViewModel(repository, alarmScheduler, medicationId, extras.createSavedStateHandle()) as T
    }
}
