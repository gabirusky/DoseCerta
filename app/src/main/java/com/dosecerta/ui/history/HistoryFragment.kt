package com.dosecerta.ui.history

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dosecerta.R
import com.dosecerta.ui.stackForReadingSize
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.dao.MedicationLogWithDetails
import com.dosecerta.data.model.MedicationStatus
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.FragmentHistoryBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels {
        object : AbstractSavedStateViewModelFactory(this@HistoryFragment, arguments) {
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
                val db = DoseCertaDatabase.getDatabase(requireContext())
                @Suppress("UNCHECKED_CAST")
                return HistoryViewModel(MedicationRepository(db.medicationDao(), db.scheduleDao(), db.medicationLogDao(), db), handle, requireContext().applicationContext) as T
            }
        }
    }
    private lateinit var header: com.dosecerta.databinding.ItemHistoryHeaderBinding
    private lateinit var adapter: MedicationLogAdapter
    private var progress: Snackbar? = null
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) runCatching { requireContext().contentResolver.takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        viewModel.destinationSelected(uri, requireContext().applicationContext)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = MedicationLogAdapter(::showLogOptions)
        binding.recyclerLogs.layoutManager = LinearLayoutManager(requireContext())
        header = com.dosecerta.databinding.ItemHistoryHeaderBinding.inflate(layoutInflater, binding.recyclerLogs, false)
        val headerAdapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun getItemCount() = 1
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : androidx.recyclerview.widget.RecyclerView.ViewHolder(header.root) {}
            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) = Unit
        }
        binding.recyclerLogs.adapter = androidx.recyclerview.widget.ConcatAdapter(headerAdapter, adapter)
        listOf(header.chipAll to null, header.chipTaken to MedicationStatus.TAKEN,
            header.chipMissed to MedicationStatus.MISSED, header.chipSkipped to MedicationStatus.SKIPPED).forEach { (chip, status) ->
            chip.isChecked = viewModel.selectedFilter.value == status
            chip.setOnCheckedChangeListener { _, checked -> if (checked) viewModel.updateFilter(status) }
        }
        header.summaryMetrics.stackForReadingSize()
        header.adherenceSummary.stackForReadingSize()
        header.textDateRange.setOnClickListener { viewModel.cyclePeriod() }
        binding.fabExportPdf.setOnClickListener {
            if (viewModel.beginExport(resources.configuration.locales[0].toLanguageTag(), ZoneId.systemDefault().id)) {
                createDocument.launch("DoseCerta-${java.time.LocalDate.now()}.pdf")
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.statistics.collect { s ->
                    header.textTakenCount.text = s.taken.toString()
                    header.textMissedCount.text = s.missed.toString()
                    header.textSkippedCount.text = s.skipped.toString()
                    val adherence = s.percentage?.let { "$it%" } ?: getString(R.string.report_no_data)
                    header.textAdherence.text = adherence
                    header.textAdherence.textSize = if (s.percentage == null) 13f else 22f
                    header.textAdherence.contentDescription = getString(R.string.report_adherence, adherence)
                } }
                launch { viewModel.logs.collect { rows ->
                    adapter.submitList(rows)
                    header.textEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                } }
                launch { viewModel.daysBack.collect { days -> header.textDateRange.setText(when(days) {
                    7 -> R.string.history_last_7_days; 30 -> R.string.history_last_30_days; else -> R.string.history_all_period
                }) } }
                launch { viewModel.actionError.collect { error -> if (error) {
                    Snackbar.make(binding.root, R.string.report_action_failed, Snackbar.LENGTH_LONG).show()
                    viewModel.clearActionError()
                } } }
                launch { viewModel.exportState.collect(::showExportState) }
            }
        }
    }
    override fun onResume() { super.onResume(); viewModel.refresh() }
    private fun showLogOptions(anchor: View, detail: MedicationLogWithDetails) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 0, R.string.history_mark_taken)
        popup.menu.add(0, 2, 1, R.string.history_mark_missed)
        popup.menu.add(0, 3, 2, R.string.history_mark_skipped)
        popup.menu.add(0, 4, 3, R.string.history_delete_log)
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> chooseTakenTime(detail)
                2 -> viewModel.updateLogStatus(detail.log, MedicationStatus.MISSED, null)
                3 -> viewModel.updateLogStatus(detail.log, MedicationStatus.SKIPPED, null)
                4 -> AlertDialog.Builder(requireContext()).setTitle(R.string.history_delete_confirm_title)
                    .setMessage(getString(R.string.history_delete_confirm_message, detail.medicationName.orEmpty()))
                    .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteLog(detail.log) }
                    .setNegativeButton(R.string.cancel, null).show()
            }
            true
        }
        popup.show()
    }
    private fun chooseTakenTime(detail: MedicationLogWithDetails) {
        val zone = ZoneId.systemDefault()
        val original = Instant.ofEpochMilli(detail.log.actualTime ?: detail.log.originalDueAt).atZone(zone)
        DatePickerDialog(requireContext(), { _, year, month, day ->
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val time = java.time.LocalDate.of(year, month + 1, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                viewModel.updateLogStatus(detail.log, MedicationStatus.TAKEN, time)
            }, original.hour, original.minute, android.text.format.DateFormat.is24HourFormat(requireContext()))
                .apply { setTitle(R.string.report_taken_time) }.show()
        }, original.year, original.monthValue - 1, original.dayOfMonth)
            .apply { setTitle(R.string.report_taken_date) }.show()
    }
    private fun showExportState(state: HistoryViewModel.ExportState) {
        val busy = state is HistoryViewModel.ExportState.Loading || state is HistoryViewModel.ExportState.Choosing
        binding.fabExportPdf.isEnabled = !busy
        if (state !is HistoryViewModel.ExportState.Loading) { progress?.dismiss(); progress = null }
        when(state) {
            HistoryViewModel.ExportState.Loading -> if (progress == null) {
                progress = Snackbar.make(binding.root, R.string.history_export_generating, Snackbar.LENGTH_INDEFINITE).also { it.show() }
            }
            is HistoryViewModel.ExportState.Success -> {
                AlertDialog.Builder(requireContext()).setTitle(R.string.report_saved)
                    .setMessage(getString(R.string.report_saved_destination, state.fileName,
                        ReportDocuments.destinationLabel(requireContext(), state.uri)))
                    .setPositiveButton(R.string.history_export_open) { _, _ -> openReport(state.uri) }
                    .setNeutralButton(R.string.report_share) { _, _ -> shareReport(state.uri) }
                    .setNegativeButton(android.R.string.ok, null)
                    .setOnDismissListener { viewModel.resetExportState() }.show()
            }
            HistoryViewModel.ExportState.Cancelled -> viewModel.resetExportState()
            HistoryViewModel.ExportState.Interrupted -> Snackbar.make(binding.root, R.string.report_interrupted, Snackbar.LENGTH_LONG)
                .setAction(R.string.report_retry) { viewModel.resetExportState(); binding.fabExportPdf.performClick() }.show()
            is HistoryViewModel.ExportState.Error -> Snackbar.make(binding.root, R.string.report_failed_cleanup, Snackbar.LENGTH_LONG)
                .setAction(R.string.report_retry) { viewModel.resetExportState(); binding.fabExportPdf.performClick() }.show()
            else -> Unit
        }
    }
    private fun openReport(uri: android.net.Uri) {
        try { startActivity(ReportDocuments.open(uri)) }
        catch (_: ActivityNotFoundException) { Snackbar.make(binding.root, R.string.report_no_viewer, Snackbar.LENGTH_LONG)
            .setAction(R.string.report_share) { shareReport(uri) }.show() }
        catch (_: SecurityException) { Snackbar.make(binding.root, R.string.report_access_lost, Snackbar.LENGTH_LONG).show() }
    }
    private fun shareReport(uri: android.net.Uri) {
        try { startActivity(Intent.createChooser(ReportDocuments.share(uri), getString(R.string.report_share))) }
        catch (_: ActivityNotFoundException) { Snackbar.make(binding.root, R.string.report_no_viewer, Snackbar.LENGTH_LONG).show() }
        catch (_: SecurityException) { Snackbar.make(binding.root, R.string.report_access_lost, Snackbar.LENGTH_LONG).show() }
    }
    override fun onDestroyView() { progress?.dismiss(); progress = null; binding.recyclerLogs.adapter = null; _binding = null; super.onDestroyView() }
}
