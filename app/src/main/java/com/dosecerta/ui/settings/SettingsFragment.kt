package com.dosecerta.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
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
import com.dosecerta.alarm.AlarmDiagnostics
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.alarm.ReminderCapabilityChecker
import com.dosecerta.alarm.ReminderChannels
import com.dosecerta.alarm.ReminderSettingsNavigator
import com.dosecerta.databinding.FragmentSettingsBinding
import com.dosecerta.util.SettingsPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferences: SettingsPreferences
    private var restoring = false
    // Preference writes are finite process work and survive leaving/recreating this view.
    private companion object {
        val writes = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val queuedWrites = Channel<suspend () -> Unit>(Channel.UNLIMITED)
        init { writes.launch { for (write in queuedWrites) try { write() } catch (_: Exception) { } } }
        fun enqueueWrite(write: suspend () -> Unit) { queuedWrites.trySend(write) }
    }
    private val ringtonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                else @Suppress("DEPRECATION") result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            enqueueWrite { preferences.saveAlarmSoundUri(uri?.toString()) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        preferences = SettingsPreferences(requireContext().applicationContext)
        if (resources.configuration.fontScale > 1.3f || resources.configuration.screenWidthDp < 360) {
            binding.radioGroupLanguage.orientation = android.widget.LinearLayout.VERTICAL
            listOf(binding.radioPortuguese, binding.radioEnglish).forEach {
                it.layoutParams = android.widget.RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        binding.textAppVersion.text = getString(R.string.reminder_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        binding.cardPrivacyPolicy.setOnClickListener { findNavController().navigate(R.id.action_nav_settings_to_nav_privacy_policy) }
        binding.radioGroupLanguage.setOnCheckedChangeListener { _, checked ->
            if (!restoring) {
                val language = if (checked == R.id.radio_english) SettingsPreferences.LANGUAGE_ENGLISH else SettingsPreferences.LANGUAGE_PORTUGUESE
                enqueueWrite { preferences.saveLanguage(language) }
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
            }
        }
        binding.sliderMissedReminderHours.addOnChangeListener { _, value, fromUser ->
            binding.textMissedReminderHours.text = getString(R.string.reminder_hours, value.toInt())
            if (fromUser) enqueueWrite { preferences.saveMissedReminderHours(value.toInt()) }
        }
        binding.switchLockDetails.setOnCheckedChangeListener { _, checked -> if (!restoring) enqueueWrite { preferences.saveShowMedicationOnLockScreen(checked) } }
        binding.cardAlarmSound.setOnClickListener { selectSound() }
        binding.buttonNotificationsSettings.setOnClickListener {
            val checker = ReminderCapabilityChecker(requireContext())
            val state = checker.check()
            val channel = when { !state.notificationPermission || !state.notificationsEnabled -> null
                !state.alarmChannelEnabled -> ReminderChannels.ALARM
                !state.reminderChannelEnabled -> ReminderChannels.REMINDER
                else -> ReminderChannels.ALARM }
            openSettings(checker.notificationsSettings(channel))
        }
        binding.buttonExactSettings.setOnClickListener { openSettings(ReminderCapabilityChecker(requireContext()).exactAlarmSettings()) }
        binding.buttonFullScreenSettings.setOnClickListener { openSettings(ReminderCapabilityChecker(requireContext()).fullScreenSettings()) }
        binding.buttonDiagnostics.setOnClickListener {
            val diagnostic = AlarmDiagnostics.read(requireContext()).ifBlank { getString(R.string.reminder_diagnostics_empty) }
            val text = android.widget.TextView(requireContext()).apply { this.text = diagnostic; setTextIsSelectable(true); setPadding(24, 16, 24, 16) }
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.reminder_diagnostics)
                .setView(android.widget.ScrollView(requireContext()).apply { addView(text) }).setPositiveButton(R.string.ok, null).show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            preferences.selectedLanguage.collect { language ->
                restoring = true
                binding.radioGroupLanguage.check(if (language == SettingsPreferences.LANGUAGE_ENGLISH) R.id.radio_english else R.id.radio_portuguese)
                restoring = false
            }
        }
        viewLifecycleOwner.lifecycleScope.launch { preferences.missedReminderHours.collect {
            binding.sliderMissedReminderHours.value = it.toFloat(); binding.textMissedReminderHours.text = getString(R.string.reminder_hours, it)
        } }
        viewLifecycleOwner.lifecycleScope.launch { preferences.showMedicationOnLockScreen.collect { restoring = true; binding.switchLockDetails.isChecked = it; restoring = false } }
        viewLifecycleOwner.lifecycleScope.launch { preferences.alarmSoundUri.collect { displaySound(it?.let(Uri::parse)) } }
    }
    override fun onResume() {
        super.onResume()
        if (_binding == null) return
        updateCapabilities()
        val context = requireContext().applicationContext
        writes.launch { try { AlarmScheduler(context).reconcile() } catch (error: Exception) { AlarmDiagnostics.record(context, "settings_reconcile", result = error.javaClass.simpleName) } }
    }
    private fun updateCapabilities() {
        val state = ReminderCapabilityChecker(requireContext()).check()
        val lines = listOf(
            getString(if (state.canNotifyAlarm) R.string.reminder_notifications_ready else R.string.reminder_notifications_blocked),
            getString(if (state.canNotifyReminder) R.string.reminder_followups_ready else R.string.reminder_followups_blocked),
            getString(if (state.exactAlarms) R.string.reminder_exact_ready else R.string.reminder_exact_degraded),
            getString(if (state.fullScreenIntent) R.string.reminder_fullscreen_ready else R.string.reminder_fullscreen_blocked),
            getString(R.string.reminder_device_limits))
        binding.textReminderStatus.text = lines.joinToString("\n")
        binding.buttonExactSettings.visibility = if (Build.VERSION.SDK_INT >= 31) View.VISIBLE else View.GONE
        binding.buttonFullScreenSettings.visibility = if (Build.VERSION.SDK_INT >= 34) View.VISIBLE else View.GONE
    }
    private fun openSettings(intent: Intent) {
        if (!ReminderSettingsNavigator.open(this, intent)) Toast.makeText(requireContext(), R.string.reminder_settings_unavailable, Toast.LENGTH_LONG).show()
    }
    private fun selectSound() {
        viewLifecycleOwner.lifecycleScope.launch {
            val current = preferences.getAlarmSoundUriSync()?.let(Uri::parse)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.settings_alarm_sound))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, current).putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            try { ringtonePicker.launch(intent) }
            catch (_: RuntimeException) { Toast.makeText(requireContext(), R.string.reminder_settings_unavailable, Toast.LENGTH_LONG).show() }
        }
    }
    private suspend fun displaySound(uri: Uri?) {
        val context = requireContext().applicationContext
        val defaultTitle = getString(R.string.settings_alarm_sound_default)
        val recoveredTitle = getString(R.string.reminder_sound_recovered)
        val title = withContext(Dispatchers.IO) {
            if (uri == null) defaultTitle
            else try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { }
                    ?: throw IllegalArgumentException("unreadable")
                RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: throw IllegalArgumentException("unreadable")
            } catch (_: Exception) {
                preferences.saveAlarmSoundUri(null)
                recoveredTitle
            }
        }
        _binding?.textCurrentAlarmSound?.text = title
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
