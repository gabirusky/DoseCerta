package com.dosecerta.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.FragmentHomeBinding
import com.dosecerta.util.DateTimeUtils
import kotlinx.coroutines.launch

/**
 * Home fragment showing today's medication schedule.
 */
class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: HomeViewModel by viewModels {
        val database = DoseCertaDatabase.getDatabase(requireContext())
        val repository = MedicationRepository(
            database.medicationDao(),
            database.scheduleDao(),
            database.medicationLogDao()
        )
        HomeViewModelFactory(repository)
    }
    
    private lateinit var adapter: ScheduleAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupRecyclerView()
        observeViewModel()
    }
    
    private fun setupUI() {
        binding.textGreeting.text = DateTimeUtils.getGreeting()
    }
    
    private fun setupRecyclerView() {
        adapter = ScheduleAdapter(
            onTakeClick = { scheduleItem ->
                lifecycleScope.launch {
                    viewModel.markAsTaken(scheduleItem)
                }
            },
            onSnoozeClick = { scheduleItem ->
                lifecycleScope.launch {
                    viewModel.snoozeReminder(scheduleItem)
                }
            }
        )
        
        binding.recyclerMedications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMedications.adapter = adapter
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.todaySchedule.collect { schedule ->
                adapter.submitList(schedule)
                updateEmptyState(schedule.isEmpty())
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statistics.collect { stats ->
                binding.textAdherencePercentage.text = "${stats.adherencePercentage}%"
                binding.textActiveMedicationsCount.text = stats.takenCount.toString()
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.recyclerMedications.visibility = View.GONE
            binding.textNoMedications.visibility = View.VISIBLE
        } else {
            binding.recyclerMedications.visibility = View.VISIBLE
            binding.textNoMedications.visibility = View.GONE
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class HomeViewModelFactory(private val repository: MedicationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
