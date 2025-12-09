package com.dosecerta.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.dosecerta.R
import com.dosecerta.databinding.FragmentSetupTermsBinding
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.launch

/**
 * Step 1 of setup: Terms and Privacy Policy acceptance.
 */
class SetupTermsFragment : Fragment() {
    
    private var _binding: FragmentSetupTermsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var settingsPreferences: SettingsPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupTermsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsPreferences = SettingsPreferences(requireContext())
        
        setupCheckbox()
        setupContinueButton()
    }
    
    private fun setupCheckbox() {
        binding.checkboxAccept.setOnCheckedChangeListener { _, isChecked ->
            binding.buttonContinue.isEnabled = isChecked
        }
    }
    
    private fun setupContinueButton() {
        binding.buttonContinue.setOnClickListener {
            lifecycleScope.launch {
                // Save that terms have been accepted
                settingsPreferences.acceptTerms()
                
                // Navigate to notifications step
                findNavController().navigate(R.id.action_terms_to_notifications)
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
