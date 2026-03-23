package com.dosecerta.ui.history

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.google.android.material.snackbar.Snackbar
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

    // ─── Permission launcher (legacy API 26-28 storage) ───────────────────────
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) triggerExport()
        else Snackbar.make(binding.root, R.string.history_export_error, Snackbar.LENGTH_SHORT).show()
    }
    
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
        setupFab()
        observeViewModel()
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
    
    private fun setupRecyclerView() {
        adapter = MedicationLogAdapter { view, logWithDetails ->
            showLogOptionsPopup(view, logWithDetails)
        }
        binding.recyclerLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLogs.adapter = adapter
    }
    
    private fun showLogOptionsPopup(anchorView: View, logWithDetails: MedicationLogWithDetails) {
        val log = logWithDetails.log
        val currentStatus = log.status
        
        val popup = PopupMenu(requireContext(), anchorView)
        
        if (currentStatus != MedicationStatus.TAKEN)
            popup.menu.add(0, 1, 0, getString(R.string.history_mark_taken))
        if (currentStatus != MedicationStatus.MISSED)
            popup.menu.add(0, 2, 1, getString(R.string.history_mark_missed))
        if (currentStatus != MedicationStatus.SKIPPED)
            popup.menu.add(0, 3, 2, getString(R.string.history_mark_skipped))
        popup.menu.add(0, 4, 3, getString(R.string.history_delete_log))
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> viewModel.updateLogStatus(log, MedicationStatus.TAKEN)
                2 -> viewModel.updateLogStatus(log, MedicationStatus.MISSED)
                3 -> viewModel.updateLogStatus(log, MedicationStatus.SKIPPED)
                4 -> showDeleteConfirmation(logWithDetails)
            }
            true
        }
        popup.show()
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
            viewModel.cyclePeriod()
        }
    }

    // ─── FAB ─────────────────────────────────────────────────────────────────

    private fun setupFab() {
        binding.fabExportPdf.setOnClickListener {
            checkPermissionAndExport()
        }
    }

    private fun checkPermissionAndExport() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // API 26–28: need WRITE_EXTERNAL_STORAGE
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> triggerExport()

                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ->
                    Snackbar.make(binding.root, R.string.history_export_error, Snackbar.LENGTH_LONG).show()

                else -> storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            // API 29+: MediaStore, no runtime permission needed
            triggerExport()
        }
    }

    private fun triggerExport() {
        val periodLabel = binding.textDateRange.text.toString()
        viewModel.exportPdf(requireContext().applicationContext, periodLabel)
    }
    
    // ─── Observers ───────────────────────────────────────────────────────────

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
                binding.textDateRange.text = when (days) {
                    7    -> getString(R.string.history_last_7_days)
                    30   -> getString(R.string.history_last_30_days)
                    else -> getString(R.string.history_all_period)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.exportState.collect { state ->
                when (state) {
                    is HistoryViewModel.ExportState.Loading -> {
                        binding.fabExportPdf.isEnabled = false
                        Snackbar.make(
                            binding.root,
                            R.string.history_export_generating,
                            Snackbar.LENGTH_INDEFINITE
                        ).show()
                    }
                    is HistoryViewModel.ExportState.Success -> {
                        binding.fabExportPdf.isEnabled = true
                        val uri = state.uri
                        Snackbar.make(
                            binding.root,
                            R.string.history_export_success,
                            Snackbar.LENGTH_LONG
                        ).setAction(R.string.history_export_open) {
                            try {
                                val intent = PdfReportGenerator(requireContext()).openIntent(uri)
                                startActivity(intent)
                            } catch (e: Exception) {
                                // No PDF viewer installed — do nothing
                            }
                        }.show()
                        viewModel.resetExportState()
                    }
                    is HistoryViewModel.ExportState.Error -> {
                        binding.fabExportPdf.isEnabled = true
                        Snackbar.make(
                            binding.root,
                            getString(R.string.history_export_error) + ": ${state.message}",
                            Snackbar.LENGTH_LONG
                        ).show()
                        viewModel.resetExportState()
                    }
                    else -> {
                        binding.fabExportPdf.isEnabled = true
                    }
                }
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        binding.recyclerLogs.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.textEmpty.visibility    = if (isEmpty) View.VISIBLE else View.GONE
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

