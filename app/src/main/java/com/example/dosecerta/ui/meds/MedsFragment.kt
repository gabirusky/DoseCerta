package com.example.dosecerta.ui.meds

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.dosecerta.DoseCertaApplication
import com.example.dosecerta.R // Import R class for resource IDs
import com.example.dosecerta.databinding.FragmentMedsBinding
import com.example.dosecerta.ui.ViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder // For delete confirmation

class MedsFragment : Fragment() {

    private var _binding: FragmentMedsBinding? = null
    private val binding get() = _binding!!

    private val medsViewModel: MedsViewModel by viewModels { ViewModelFactory }

    private lateinit var medicationAdapter: MedicationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMedsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupRecyclerView()
        setupAddButtons()

        // Observe the list of medications
        medsViewModel.allMedications.observe(viewLifecycleOwner) {
            medications ->
            medicationAdapter.submitList(medications)
            // Update placeholder visibility
            if (medications.isEmpty()) {
                binding.medsRecyclerView.visibility = View.GONE
            } else {
                binding.medsRecyclerView.visibility = View.VISIBLE
            }
        }

        return root
    }

    private fun setupRecyclerView() {
        medicationAdapter = MedicationAdapter(
            onEditClick = { medication ->
                // Navigate to edit screen, passing medication ID
                val action = MedsFragmentDirections.actionNavigationMedsToAddEditMedFragment(medication.id)
                findNavController().navigate(action)
            },
            onDeleteClick = { medication ->
                 // Show confirmation dialog before deleting
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Medication")
                    .setMessage("Are you sure you want to delete ${medication.name}?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        medsViewModel.deleteMedication(medication)
                    }
                    .show()
            }
        )
        binding.medsRecyclerView.adapter = medicationAdapter
        binding.medsRecyclerView.layoutManager = LinearLayoutManager(context)
        // TODO: Add swipe-to-delete or other item interactions if desired
    }

    private fun setupAddButtons() {
        // FAB click listener
        binding.fabAddMedication.setOnClickListener {
             navigateToAddEditScreen()
        }

    }

    private fun navigateToAddEditScreen(medicationId: Int = -1) { // Default to -1 for Add new
         val action = MedsFragmentDirections.actionNavigationMedsToAddEditMedFragment(medicationId)
         findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Prevent memory leaks with RecyclerView adapter
        binding.medsRecyclerView.adapter = null 
        _binding = null
    }
} 