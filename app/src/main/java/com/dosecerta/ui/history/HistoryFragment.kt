package com.dosecerta.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.dao.MedicationLogWithDetails
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

/**
 * History fragment showing medication logs and statistics.
 */
class HistoryFragment : Fragment() {
    
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HistoryViewModel by viewModels {
        val database = DoseCertaDatabase.getDatabase(requireContext())
        val repository = MedicationRepository(
            database.medicationDao(),
            database.scheduleDao(),
            database.medicationLogDao()
        )
        HistoryViewModelFactory(repository)
    }
    
    private lateinit var adapter: MedicationLogAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupFilterChips()
        setupDateRange()
        observeViewModel()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data whenever the history page is opened or returns to foreground
        viewModel.refresh()
    }
    
    private fun setupRecyclerView() {
        adapter = MedicationLogAdapter { logWithDetails ->
            showLogOptionsDialog(logWithDetails)
        }
        binding.recyclerLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLogs.adapter = adapter
    }
    
    private fun showLogOptionsDialog(logWithDetails: MedicationLogWithDetails) {
        val log = logWithDetails.log
        val currentStatus = log.status
        
        // Build options based on current status
        val options = mutableListOf<String>()
        val statuses = mutableListOf<MedicationStatus?>()
        
        // Add status change options (show the two statuses that are NOT the current one)
        if (currentStatus != MedicationStatus.TAKEN) {
            options.add(getString(R.string.history_mark_taken))
            statuses.add(MedicationStatus.TAKEN)
        }
        if (currentStatus != MedicationStatus.MISSED) {
            options.add(getString(R.string.history_mark_missed))
            statuses.add(MedicationStatus.MISSED)
        }
        if (currentStatus != MedicationStatus.SKIPPED) {
            options.add(getString(R.string.history_mark_skipped))
            statuses.add(MedicationStatus.SKIPPED)
        }
        
        // Add delete option
        options.add(getString(R.string.history_delete_log))
        statuses.add(null) // null indicates delete action
        
        // Get display name for dialog title
        val displayName = log.customMedicationName ?: logWithDetails.medicationName ?: ""
        
        AlertDialog.Builder(requireContext())
            .setTitle(displayName)
            .setItems(options.toTypedArray()) { _, which ->
                val selectedStatus = statuses[which]
                if (selectedStatus != null) {
                    viewModel.updateLogStatus(log, selectedStatus)
                } else {
                    // Delete action - show confirmation
                    showDeleteConfirmation(logWithDetails)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDeleteConfirmation(logWithDetails: MedicationLogWithDetails) {
        val displayName = logWithDetails.log.customMedicationName 
            ?: logWithDetails.medicationName ?: ""
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.history_delete_confirm_title)
            .setMessage(getString(R.string.history_delete_confirm_message, displayName))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteLog(logWithDetails.log)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun setupFilterChips() {
        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.updateFilter(null)
        }
        
        binding.chipTaken.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.updateFilter(MedicationStatus.TAKEN)
        }
        
        binding.chipMissed.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.updateFilter(MedicationStatus.MISSED)
        }
        
        binding.chipSkipped.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.updateFilter(MedicationStatus.SKIPPED)
        }
    }
    
    private fun setupDateRange() {
        binding.textDateRange.setOnClickListener {
            // Toggle between 7 and 30 days
            val current = viewModel.daysBack.value
            viewModel.updateDaysBack(if (current == 7) 30 else 7)
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statistics.collect { stats ->
                binding.textTakenCount.text = stats.taken.toString()
                binding.textMissedCount.text = stats.missed.toString()
                binding.textSkippedCount.text = stats.skipped.toString()
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.logs.collect { logs ->
                adapter.submitList(logs)
                updateEmptyState(logs.isEmpty())
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.daysBack.collect { days ->
                binding.textDateRange.text = "Últimos $days dias"
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.recyclerLogs.visibility = View.GONE
            binding.textEmpty.visibility = View.VISIBLE
        } else {
            binding.recyclerLogs.visibility = View.VISIBLE
            binding.textEmpty.visibility = View.GONE
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class HistoryViewModelFactory(private val repository: MedicationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

