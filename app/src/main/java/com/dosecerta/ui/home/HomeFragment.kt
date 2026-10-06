package com.dosecerta.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dosecerta.R
import androidx.constraintlayout.widget.ConstraintSet
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.DialogExtraDoseBinding
import com.dosecerta.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels {
        val db = DoseCertaDatabase.getDatabase(requireContext())
        HomeViewModelFactory(MedicationRepository(db.medicationDao(), db.scheduleDao(), db.medicationLogDao()),
            com.dosecerta.alarm.AlarmScheduler(requireContext()), requireContext().applicationContext)
    }
    private lateinit var adapter: ScheduleAdapter
    private lateinit var asNeededAdapter: AsNeededMedicationAdapter
    private var doseDialog: androidx.appcompat.app.AlertDialog? = null
    private var confirmationVisible = false
    private var recording = false
    private val runningActions = mutableSetOf<String>()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false); return binding.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        // Keep the number inside the ring at the user's font size, including 100%.
        val density = resources.displayMetrics.density
        val ringSize = maxOf((72 * density).toInt(), kotlin.math.ceil(
            binding.textAdherencePercentageCard.paint.measureText("100%") + 16 * density
        ).toInt())
        binding.adherenceGraph.layoutParams = binding.adherenceGraph.layoutParams.apply {
            width = ringSize
            height = ringSize
        }
        binding.progressAdherence.indicatorSize = ringSize
        if (resources.configuration.fontScale > 1.3f || resources.configuration.screenWidthDp < 350) {
            // Let the greeting span the card; put the large ring beside its label below it.
            ConstraintSet().apply {
                clone(binding.heroContent)
                connect(R.id.text_greeting, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                connect(R.id.text_date, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                connect(R.id.adherence_graph, ConstraintSet.TOP, R.id.text_date, ConstraintSet.BOTTOM, (4 * density).toInt())
                connect(R.id.text_week_adherence, ConstraintSet.TOP, R.id.adherence_graph, ConstraintSet.TOP)
                connect(R.id.text_week_adherence, ConstraintSet.BOTTOM, R.id.adherence_graph, ConstraintSet.BOTTOM)
                applyTo(binding.heroContent)
            }
        }
        adapter = ScheduleAdapter(onTakeClick = { action(it, true) }, onSkipClick = { action(it, false) })
        binding.recyclerMedications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMedications.adapter = adapter
        asNeededAdapter = AsNeededMedicationAdapter { medication -> confirmExtra(medication.name, { viewModel.recordExtraDose(medication.id) }) }
        binding.recyclerAsNeeded.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAsNeeded.adapter = asNeededAdapter
        val upcomingAdapter = UpcomingDoseAdapter()
        binding.recyclerUpcoming.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerUpcoming.adapter = upcomingAdapter
        binding.buttonAddMedication.setOnClickListener { findNavController().navigate(R.id.nav_add_medication) }
        binding.buttonRegisterExtraDose.setOnClickListener { showExtraDoseDialog() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.todaySchedule.collect { doses -> adapter.submitList(doses.sortedWith(compareBy<com.dosecerta.data.model.ScheduleItem> { it.status != com.dosecerta.data.model.MedicationStatus.PENDING }.thenBy { it.scheduledTime }))
                    binding.textNoMedications.visibility = if (doses.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerMedications.visibility = if (doses.isEmpty()) View.GONE else View.VISIBLE
                } }
                launch { viewModel.upcoming.collect { upcoming ->
                    upcomingAdapter.submitList(upcoming)
                    binding.textUpcoming.visibility = if (upcoming.isEmpty()) View.VISIBLE else View.GONE
                    binding.recyclerUpcoming.visibility = if (upcoming.isEmpty()) View.GONE else View.VISIBLE
                } }
                launch { viewModel.statistics.collect { stats ->
                    binding.textAdherencePercentageCard.text = stats.percentage?.let { "$it%" } ?: getString(R.string.ui_no_data)
                    binding.textAdherencePercentageCard.textSize = if (stats.percentage == null) 13f else 22f
                    binding.adherenceGraph.contentDescription = getString(R.string.design_week_adherence_value,
                        binding.textAdherencePercentageCard.text)
                    binding.progressAdherence.setProgressCompat(stats.percentage ?: 0, true)
                } }
                launch { viewModel.asNeededMedications.collect { meds -> asNeededAdapter.submitList(meds)
                    binding.sectionAsNeeded.visibility = if (meds.isEmpty()) View.GONE else View.VISIBLE
                } }
                launch { viewModel.clock.collect { now ->
                    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
                    binding.textGreeting.setText(when (hour) { in 0..11 -> R.string.home_greeting_morning; in 12..17 -> R.string.home_greeting_afternoon; else -> R.string.home_greeting_evening })
                    binding.textDate.text = com.dosecerta.ui.UiDateTime.date(requireContext(), now)
                } }
            }
        }
    }
    private fun action(item: com.dosecerta.data.model.ScheduleItem, take: Boolean) {
        val key = "${item.schedule.id}:${item.scheduledTime}"
        if (!runningActions.add(key)) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val saved = if (take) viewModel.markAsTaken(item) else viewModel.skipMedication(item)
                if (!saved) Toast.makeText(requireContext(), R.string.ui_action_error, Toast.LENGTH_LONG).show()
            } catch (e: Exception) { Toast.makeText(requireContext(), R.string.ui_action_error, Toast.LENGTH_LONG).show() }
            finally { runningActions.remove(key) }
        }
    }
    private fun confirmExtra(name: String, record: suspend () -> Unit, after: () -> Unit = {}) {
        if (confirmationVisible || recording) return
        confirmationVisible = true
        val dialog = MaterialAlertDialogBuilder(requireContext()).setMessage(getString(R.string.ui_extra_confirm, name))
            .setPositiveButton(R.string.ui_take, null).setNegativeButton(R.string.cancel, null).create()
        dialog.setOnDismissListener { confirmationVisible = false }
        dialog.setOnShowListener { dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (recording) return@setOnClickListener
            recording = true
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = false
            dialog.setCancelable(false)
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    record()
                    Toast.makeText(requireContext(), R.string.extra_dose_recorded, Toast.LENGTH_SHORT).show()
                    dialog.dismiss(); after()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.ui_action_error, Toast.LENGTH_LONG).show()
                    dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    dialog.setCancelable(true)
                } finally { recording = false }
            }
        } }
        dialog.show()
    }
    private fun showExtraDoseDialog() {
        if (doseDialog?.isShowing == true) return
        val content = DialogExtraDoseBinding.inflate(layoutInflater)
        val meds = ExtraDoseMedicationAdapter { med -> confirmExtra(med.name, { viewModel.recordExtraDose(med.id) }) { doseDialog?.dismiss() } }
        content.recyclerMedications.layoutManager = LinearLayoutManager(requireContext())
        content.recyclerMedications.adapter = meds
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(content.root).setNegativeButton(R.string.cancel, null).create()
        doseDialog = dialog
        var listJob: Job? = null
        dialog.setOnShowListener {
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.85).toInt())
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            listJob = viewLifecycleOwner.lifecycleScope.launch { viewModel.activeMedications.collect { list ->
                meds.submitList(list)
                content.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                content.recyclerMedications.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            } }
        }
        dialog.setOnDismissListener { listJob?.cancel(); doseDialog = null }
        content.buttonAddCustom.setOnClickListener {
            val name = content.editCustomMedication.text.toString().trim()
            content.inputCustom.error = if (name.isEmpty()) getString(R.string.extra_dose_error_empty) else null
            if (name.isNotEmpty()) confirmExtra(name, { viewModel.recordCustomExtraDose(name) }) { dialog.dismiss() }
        }
        dialog.show()
    }
    override fun onDestroyView() { doseDialog?.dismiss(); _binding = null; super.onDestroyView() }
}
class HomeViewModelFactory(private val repository: MedicationRepository, private val alarmScheduler: com.dosecerta.alarm.AlarmScheduler,
    private val context: android.content.Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST") return HomeViewModel(repository, alarmScheduler, extras.createSavedStateHandle()) as T
    }
}
