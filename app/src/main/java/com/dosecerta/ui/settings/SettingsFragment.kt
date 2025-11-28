package com.dosecerta.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.dosecerta.BuildConfig
import com.dosecerta.R
import com.dosecerta.databinding.FragmentSettingsBinding
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings fragment for app configuration.
 */
class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var settingsPreferences: SettingsPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        settingsPreferences = SettingsPreferences(requireContext())
        
        setupLanguageSelection()
        setupPrivacyPolicyLink()
        setupAppVersion()
    }
    
    private fun setupLanguageSelection() {
        // Load saved language preference
        lifecycleScope.launch {
            val savedLanguage = settingsPreferences.selectedLanguage.first()
            
            when (savedLanguage) {
                SettingsPreferences.LANGUAGE_PORTUGUESE -> binding.radioPortuguese.isChecked = true
                SettingsPreferences.LANGUAGE_ENGLISH -> binding.radioEnglish.isChecked = true
            }
        }
        
        // Handle language changes
        binding.radioGroupLanguage.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_portuguese -> {
                    changeLanguage(SettingsPreferences.LANGUAGE_PORTUGUESE)
                }
                R.id.radio_english -> {
                    changeLanguage(SettingsPreferences.LANGUAGE_ENGLISH)
                }
            }
        }
    }
    
    private fun changeLanguage(languageCode: String) {
        lifecycleScope.launch {
            // Save preference
            settingsPreferences.saveLanguage(languageCode)
            
            // Apply language change
            val locale = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(locale)
            
            // Restart activity to apply changes
            requireActivity().recreate()
        }
    }
    
    private fun setupPrivacyPolicyLink() {
        binding.cardPrivacyPolicy.setOnClickListener {
            // Navigate to privacy policy screen
            findNavController().navigate(R.id.action_nav_settings_to_nav_privacy_policy)
        }
    }
    
    private fun setupAppVersion() {
        binding.textAppVersion.text = getString(R.string.settings_version)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
