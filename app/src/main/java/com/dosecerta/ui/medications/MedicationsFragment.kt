package com.dosecerta.ui.medications

import android.os.Bundle
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.FragmentMedicationsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Medications fragment showing all medications with search and filter.
 */
class MedicationsFragment : Fragment() {
    
    private var _binding: FragmentMedicationsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MedicationsViewModel by viewModels {
        val database = DoseCertaDatabase.getDatabase(requireContext())
        val repository = MedicationRepository(
            database.medicationDao(),
            database.scheduleDao(),
            database.medicationLogDao()
        )
        MedicationsViewModelFactory(repository)
    }
    
    private lateinit var adapter: MedicationAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedicationsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupSearchView()
        setupFilterChips()
        setupFab()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        adapter = MedicationAdapter(
            onEditClick = { medication ->
                // TODO: Navigate to edit screen
                // findNavController().navigate(...)
            },
            onDeleteClick = { medication ->
                showDeleteConfirmation(medication)
            }
        )
        
        binding.recyclerMedications.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerMedications.adapter = adapter
    }
    
    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.updateSearchQuery(newText ?: "")
                return true
            }
        })
    }
    
    private fun setupFilterChips() {
        binding.chipAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.updateFilter(null)
        }
        
        binding.chipDaily.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.updateFilter(Frequency.DAILY)
        }
        
        binding.chipAsNeeded.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) viewModel.updateFilter(Frequency.AS_NEEDED)
        }
    }
    
    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            // TODO: Navigate to add medication screen
            // findNavController().navigate(...)
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.medications.collect { medications ->
                adapter.submitList(medications)
                updateEmptyState(medications.isEmpty())
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.recyclerMedications.visibility = View.GONE
            binding.textEmpty.visibility = View.VISIBLE
        } else {
            binding.recyclerMedications.visibility = View.VISIBLE
            binding.textEmpty.visibility = View.GONE
        }
    }
    
    private fun showDeleteConfirmation(medication: com.dosecerta.data.local.entity.Medication) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.medications_delete_confirm)
            .setMessage("${medication.name}?")
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteMedication(medication)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class MedicationsViewModelFactory(private val repository: MedicationRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MedicationsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
