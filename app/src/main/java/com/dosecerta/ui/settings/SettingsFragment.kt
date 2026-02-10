package com.dosecerta.ui.settings

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.dosecerta.BuildConfig
import com.dosecerta.R
import com.dosecerta.databinding.FragmentSettingsBinding
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings fragment for app configuration.
 */
class SettingsFragment : Fragment() {
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var settingsPreferences: SettingsPreferences
    
    // Debounce job for slider changes
    private var sliderSaveJob: Job? = null
    
    // Ringtone picker result launcher
    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            lifecycleScope.launch {
                settingsPreferences.saveAlarmSoundUri(uri?.toString())
                updateAlarmSoundDisplay(uri)
            }
        }
    }
    
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
        setupMissedReminderSlider()
        setupAlarmSoundSelector()
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
    
    private fun setupMissedReminderSlider() {
        // Load saved preference
        lifecycleScope.launch {
            val savedHours = settingsPreferences.getMissedReminderHoursSync()
            binding.sliderMissedReminderHours.value = savedHours.toFloat()
            binding.textMissedReminderHours.text = "${savedHours}h"
        }
        
        // Handle slider changes with debounce (4 seconds delay)
        binding.sliderMissedReminderHours.addOnChangeListener { _, value, fromUser ->
            val hours = value.toInt()
            binding.textMissedReminderHours.text = "${hours}h"
            
            if (fromUser) {
                // Cancel any pending save job
                sliderSaveJob?.cancel()
                
                // Start new save job with 4 second delay
                sliderSaveJob = lifecycleScope.launch {
                    delay(4000) // Wait 4 seconds
                    settingsPreferences.saveMissedReminderHours(hours)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.settings_missed_reminder_updated, hours),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun setupAlarmSoundSelector() {
        // Load and display current alarm sound
        lifecycleScope.launch {
            val currentUri = settingsPreferences.getAlarmSoundUriSync()
            updateAlarmSoundDisplay(currentUri?.let { Uri.parse(it) })
        }
        
        // Handle click to open ringtone picker
        binding.cardAlarmSound.setOnClickListener {
            lifecycleScope.launch {
                val currentUriString = settingsPreferences.getAlarmSoundUriSync()
                val currentUri = currentUriString?.let { Uri.parse(it) }
                
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.settings_alarm_sound))
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                }
                
                ringtonePickerLauncher.launch(intent)
            }
        }
    }
    
    private fun updateAlarmSoundDisplay(uri: Uri?) {
        val displayName = if (uri != null) {
            try {
                val ringtone = RingtoneManager.getRingtone(requireContext(), uri)
                ringtone.getTitle(requireContext())
            } catch (e: Exception) {
                getString(R.string.settings_alarm_sound_default)
            }
        } else {
            getString(R.string.settings_alarm_sound_default)
        }
        
        binding.textCurrentAlarmSound.text = displayName
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
        sliderSaveJob?.cancel()
        _binding = null
    }
}
