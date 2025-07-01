package com.example.dosecerta.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dosecerta.R
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.MedicationLog
import com.example.dosecerta.databinding.FragmentHistoryBinding
import com.example.dosecerta.ui.ViewModelFactory

class HistoryFragment : Fragment(), OnLogOptionsClickListener {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val historyViewModel: HistoryViewModel by viewModels { ViewModelFactory }
    private lateinit var logAdapter: MedicationLogAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()
        setupFilterButtons()

        historyViewModel.filteredLogs.observe(viewLifecycleOwner) { logs ->
             logAdapter.submitList(logs)
             // Update placeholder visibility
             binding.textHistoryPlaceholder.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
             binding.historyRecyclerView.visibility = if (logs.isEmpty()) View.GONE else View.VISIBLE
        }
        
        // Optional: Set initial date range filter in ViewModel if needed
        // historyViewModel.setDateRangeLast7Days() // Example

        return root
    }

    private fun setupRecyclerView() {
        logAdapter = MedicationLogAdapter(this)
        binding.historyRecyclerView.adapter = logAdapter
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupFilterButtons() {
        binding.filterToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) { 
                val newStatusFilter = when (checkedId) {
                    R.id.button_filter_taken -> LogStatus.TAKEN
                    R.id.button_filter_missed -> LogStatus.MISSED
                    R.id.button_filter_skipped -> LogStatus.SKIPPED
                    else -> null // R.id.button_filter_all
                }
                historyViewModel.setFilterType(newStatusFilter)
            }
        }
        // Ensure initial check matches ViewModel state if needed, though default is All/null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.historyRecyclerView.adapter = null // Clear adapter reference
        _binding = null
    }

    override fun onMarkTakenClick(log: MedicationLog) {
        historyViewModel.updateLogStatus(log.id, LogStatus.TAKEN)
    }

    override fun onMarkSkippedClick(log: MedicationLog) {
         historyViewModel.updateLogStatus(log.id, LogStatus.SKIPPED)
    }

    override fun onMarkMissedClick(log: MedicationLog) {
        historyViewModel.updateLogStatus(log.id, LogStatus.MISSED)
    }

    override fun onRemoveLogClick(log: MedicationLog) {
        // Optional: Show confirmation dialog before deleting
        historyViewModel.deleteLog(log.id)
    }
} 