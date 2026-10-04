package com.dosecerta.ui.medications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
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
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.FragmentMedicationsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MedicationsFragment : Fragment() {
    private var _binding: FragmentMedicationsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MedicationsViewModel by viewModels {
        val db = DoseCertaDatabase.getDatabase(requireContext())
        MedicationsViewModelFactory(MedicationRepository(db.medicationDao(), db.scheduleDao(), db.medicationLogDao()), com.dosecerta.alarm.AlarmScheduler(requireContext()))
    }
    private lateinit var adapter: MedicationAdapter
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentMedicationsBinding.inflate(inflater, container, false); return binding.root
    }
    override fun onViewCreated(view: View, state: Bundle?) {
        adapter = MedicationAdapter(::edit, ::manage)
        binding.recyclerMedications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMedications.adapter = adapter
        binding.searchView.findViewById<View>(androidx.appcompat.R.id.search_plate).background = null
        binding.searchView.findViewById<android.widget.TextView>(androidx.appcompat.R.id.search_src_text).textSize = 14f
        binding.searchView.setQuery(viewModel.searchQuery.value, false)
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(value: String?): Boolean { viewModel.updateSearchQuery(value.orEmpty()); return true }
        })
        val filters = mapOf(binding.chipAll to null, binding.chipDaily to Frequency.DAILY, binding.chipEvery4Hours to Frequency.EVERY_4_HOURS,
            binding.chipEvery6Hours to Frequency.EVERY_6_HOURS, binding.chipEvery8Hours to Frequency.EVERY_8_HOURS,
            binding.chipEvery12Hours to Frequency.EVERY_12_HOURS, binding.chipWeekly to Frequency.WEEKLY,
            binding.chipMonthly to Frequency.MONTHLY, binding.chipSelectedDays to Frequency.SELECTED_DAYS, binding.chipAsNeeded to Frequency.AS_NEEDED)
        filters.forEach { (chip, frequency) -> chip.isChecked = frequency?.name == viewModel.selectedFilter.value
            chip.setOnCheckedChangeListener { _, checked -> if (checked) viewModel.updateFilter(frequency) }
        }
        binding.fabAdd.setOnClickListener { findNavController().navigate(R.id.action_medications_to_addMedication) }
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.medications.collect { list -> adapter.submitList(list)
                binding.textEmpty.setText(if (viewModel.searchQuery.value.isBlank() && viewModel.selectedFilter.value == null) R.string.medications_empty else R.string.ui_no_results)
                binding.textEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerMedications.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            } }
            launch { viewModel.upcoming.collect(adapter::updateUpcoming) }
            launch { viewModel.operation.collect { operation -> when (operation) {
                is MedicationsViewModel.Operation.Done -> { Toast.makeText(requireContext(), if (operation.archived) R.string.ui_archive_success else R.string.ui_delete_success, Toast.LENGTH_LONG).show(); viewModel.consumeOperation() }
                is MedicationsViewModel.Operation.Error -> { Toast.makeText(requireContext(), R.string.ui_action_error, Toast.LENGTH_LONG).show(); viewModel.consumeOperation() }
                else -> Unit
            } } }
        } }
    }
    private fun edit(med: Medication) {
        findNavController().navigate(R.id.action_medications_to_addMedication, Bundle().apply { putLong("medicationId", med.id) })
    }
    private fun manage(med: Medication) {
        if (viewModel.operation.value == MedicationsViewModel.Operation.Busy) return
        MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.ui_manage_medication, med.name))
            .setItems(arrayOf(getString(R.string.design_edit), getString(R.string.ui_archive), getString(R.string.ui_delete_permanent))) { _, choice ->
                if (choice == 0) edit(med)
                else if (choice == 1) MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ui_archive).setMessage(R.string.ui_archive_hint)
                    .setPositiveButton(R.string.ui_archive) { _, _ -> viewModel.archiveMedication(med) }.setNegativeButton(R.string.cancel, null).show()
                else delete(med)
            }.setNegativeButton(R.string.cancel, null).show()
    }
    private fun delete(med: Medication) {
        var deleteHistory = false
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ui_delete_permanent)
            .setSingleChoiceItems(arrayOf(getString(R.string.ui_delete_keep), getString(R.string.ui_delete_all)), 0) { _, which -> deleteHistory = which == 1 }
            .setPositiveButton(R.string.delete) { _, _ ->
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ui_delete_permanent)
                    .setMessage(getString(R.string.ui_delete_warning, med.name) + "\n\n" + getString(if (deleteHistory) R.string.ui_delete_all else R.string.ui_delete_keep))
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteMedication(med, deleteHistory) }.show()
            }.setNegativeButton(R.string.cancel, null).show()
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
class MedicationsViewModelFactory(private val repository: MedicationRepository, private val alarmScheduler: com.dosecerta.alarm.AlarmScheduler) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST") return MedicationsViewModel(repository, alarmScheduler, extras.createSavedStateHandle()) as T
    }
}
