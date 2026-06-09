package com.dosecerta.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.DialogExtraDoseBinding
import com.dosecerta.databinding.FragmentHomeBinding
import com.dosecerta.util.DateTimeUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val alarmScheduler = com.dosecerta.alarm.AlarmScheduler(requireContext())
        HomeViewModelFactory(repository, alarmScheduler, requireContext())
    }
    
    private lateinit var adapter: ScheduleAdapter
    private lateinit var asNeededAdapter: AsNeededMedicationAdapter
    
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
        
        // Set weekday as header
        binding.textMedicationsHeader.text = "${DateTimeUtils.getWeekday()}:"
        
        // Setup extra dose button
        binding.buttonRegisterExtraDose.setOnClickListener {
            showExtraDoseDialog()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ScheduleAdapter(
            onTakeClick = { scheduleItem ->
                lifecycleScope.launch {
                    viewModel.markAsTaken(scheduleItem)
                }
            },
            onSkipClick = { scheduleItem ->
                lifecycleScope.launch {
                    viewModel.skipMedication(scheduleItem)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.medication_skipped_toast, scheduleItem.medication.name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        
        binding.recyclerMedications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMedications.adapter = adapter

        // A10: Setup AS_NEEDED medications recycler
        asNeededAdapter = AsNeededMedicationAdapter { medication ->
            lifecycleScope.launch {
                viewModel.recordExtraDose(medication.id)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.extra_dose_recorded),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        binding.recyclerAsNeeded.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAsNeeded.adapter = asNeededAdapter
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
                binding.textAdherencePercentageCard.text = "${stats.adherencePercentage}%"
                
                // Update circular progress indicator in welcome card
                binding.progressAdherence.setProgressCompat(stats.adherencePercentage, true)
            }
        }

        // A10: Observe and display AS_NEEDED medications
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.asNeededMedications.collect { meds ->
                asNeededAdapter.submitList(meds)
                binding.sectionAsNeeded.visibility = if (meds.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun showExtraDoseDialog() {
        // Inflate dialog binding
        val dialogBinding = DialogExtraDoseBinding.inflate(layoutInflater)
        
        // Setup adapter
        val medicationAdapter = ExtraDoseMedicationAdapter { medication ->
            // Handle +1 click for existing medication
            lifecycleScope.launch {
                viewModel.recordExtraDose(medication.id)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.extra_dose_recorded),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        dialogBinding.recyclerMedications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = medicationAdapter
        }
        
        // Observe active medications
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeMedications.collect { medications ->
                medicationAdapter.submitList(medications)
                if (medications.isEmpty()) {
                    dialogBinding.recyclerMedications.visibility = View.GONE
                    dialogBinding.textEmpty.visibility = View.VISIBLE
                } else {
                    dialogBinding.recyclerMedications.visibility = View.VISIBLE
                    dialogBinding.textEmpty.visibility = View.GONE
                }
            }
        }
        
        // Create dialog
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()
        
        // Handle custom medication add button
        dialogBinding.buttonAddCustom.setOnClickListener {
            val customName = dialogBinding.editCustomMedication.text.toString().trim()
            if (customName.isEmpty()) {
                dialogBinding.editCustomMedication.error = getString(R.string.extra_dose_error_empty)
                return@setOnClickListener
            }
            
            lifecycleScope.launch {
                viewModel.recordCustomExtraDose(customName)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.extra_dose_recorded),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }
        
        dialog.show()
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

class HomeViewModelFactory(
    private val repository: MedicationRepository,
    private val alarmScheduler: com.dosecerta.alarm.AlarmScheduler,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, alarmScheduler, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
