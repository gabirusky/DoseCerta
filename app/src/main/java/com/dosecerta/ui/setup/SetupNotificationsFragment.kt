package com.dosecerta.ui.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dosecerta.R
import com.dosecerta.alarm.ReminderCapabilityChecker
import com.dosecerta.alarm.ReminderChannels
import com.dosecerta.databinding.FragmentSetupNotificationsBinding

/** Every settings screen requires a tap. Returning to setup only refreshes the checklist. */
class SetupNotificationsFragment : Fragment() {
    private var _binding: FragmentSetupNotificationsBinding? = null
    private val binding get() = _binding!!
    private var notificationRequested = false
    private var restoringChecklist = false
    private val manualChecks by lazy { requireContext().getSharedPreferences("setup_access_checklist", android.content.Context.MODE_PRIVATE) }
    private val requestNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (_binding != null) refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationRequested = savedInstanceState?.getBoolean("notification_requested") ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSetupNotificationsBinding.inflate(inflater, container, false)
        // Reused IDs may carry MaterialButton saved states from the previous setup UI.
        // Checkbox state comes from Android or manualChecks, so skip hierarchy restoration.
        listOf(binding.buttonNotifications, binding.buttonAlarmChannel, binding.buttonReminderChannel,
            binding.buttonExact, binding.buttonFullScreen, binding.buttonBattery,
            binding.checkAutoStartReviewed, binding.checkLockScreenReviewed)
            .forEach { it.isSaveFromParentEnabled = false }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonAllow.setOnClickListener { next() }
        binding.buttonSkip.setOnClickListener { next() }
        binding.buttonNotifications.setOnClickListener { refresh(); requestNotifications() }
        binding.buttonAlarmChannel.setOnClickListener { refresh(); open(ReminderCapabilityChecker(requireContext()).notificationsSettings(ReminderChannels.ALARM)) }
        binding.buttonReminderChannel.setOnClickListener { refresh(); open(ReminderCapabilityChecker(requireContext()).notificationsSettings(ReminderChannels.REMINDER)) }
        binding.buttonExact.setOnClickListener { refresh(); open(ReminderCapabilityChecker(requireContext()).exactAlarmSettings()) }
        binding.buttonFullScreen.setOnClickListener { refresh(); open(ReminderCapabilityChecker(requireContext()).fullScreenSettings()) }
        binding.buttonBattery.setOnClickListener { refresh(); open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), R.string.setup_access_battery_guidance) }
        binding.buttonAutoStart.setOnClickListener { open(appSettings(), R.string.setup_access_autostart_guidance) }
        binding.buttonLockScreen.setOnClickListener { open(appSettings(), R.string.setup_access_xiaomi_windows_guidance) }
        binding.checkAutoStartReviewed.setOnCheckedChangeListener { _, checked ->
            if (!restoringChecklist) manualChecks.edit().putBoolean("autostart_reviewed", checked).apply()
        }
        binding.checkLockScreenReviewed.setOnCheckedChangeListener { _, checked ->
            if (!restoringChecklist) manualChecks.edit().putBoolean("lock_screen_reviewed", checked).apply()
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) refresh()
    }

    private fun requestNotifications() {
        val checker = ReminderCapabilityChecker(requireContext())
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            (!notificationRequested || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            notificationRequested = true
            requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        open(checker.notificationsSettings())
    }

    private fun open(intent: Intent, guidance: Int? = null) {
        guidance?.let { Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show() }
        if (!launchSettings(intent) && !launchSettings(appSettings())) {
            Toast.makeText(requireContext(), R.string.reminder_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun launchSettings(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }

    private fun appSettings() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${requireContext().packageName}"))

    private fun batteryUnrestricted() = requireContext().getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(requireContext().packageName)

    private fun isXiaomiDevice(): Boolean = listOf(Build.MANUFACTURER, Build.BRAND)
        .any { it.equals("xiaomi", true) || it.equals("redmi", true) || it.equals("poco", true) }

    private fun refresh() {
        val state = ReminderCapabilityChecker(requireContext()).check()
        binding.buttonNotifications.isChecked = state.notificationPermission && state.notificationsEnabled
        binding.buttonAlarmChannel.isChecked = state.alarmChannelEnabled
        binding.buttonReminderChannel.isChecked = state.reminderChannelEnabled
        binding.buttonExact.isChecked = state.exactAlarms
        binding.buttonFullScreen.isChecked = state.fullScreenIntent
        binding.buttonExact.isEnabled = Build.VERSION.SDK_INT >= 31
        binding.buttonFullScreen.isEnabled = Build.VERSION.SDK_INT >= 34
        binding.buttonBattery.isChecked = batteryUnrestricted()
        restoringChecklist = true
        binding.checkAutoStartReviewed.isChecked = manualChecks.getBoolean("autostart_reviewed", false)
        binding.checkLockScreenReviewed.isChecked = manualChecks.getBoolean("lock_screen_reviewed", false)
        restoringChecklist = false
        binding.textNotificationsStatus.setText(if (state.canNotifyAlarm && state.canNotifyReminder) R.string.setup_access_allowed else R.string.setup_access_notifications_pending)
        binding.textExactStatus.setText(if (state.exactAlarms) R.string.setup_access_allowed else R.string.setup_access_pending)
        binding.textFullScreenStatus.setText(if (state.fullScreenIntent) R.string.setup_access_allowed else R.string.setup_access_pending)
        binding.textBatteryStatus.setText(if (batteryUnrestricted()) R.string.setup_access_battery_ready else R.string.setup_access_battery_pending)
        binding.textCapabilityStatus.setText(if (state.canNotifyAlarm && state.canNotifyReminder && state.exactAlarms && state.fullScreenIntent)
            R.string.setup_access_verified_ready else R.string.setup_access_review_pending)
        binding.buttonAllow.isEnabled = true
        binding.buttonSkip.isEnabled = true
        binding.cardXiaomi.visibility = if (isXiaomiDevice()) View.VISIBLE else View.GONE
    }

    private fun next() {
        if (findNavController().currentDestination?.id == R.id.setupNotificationsFragment)
            findNavController().navigate(R.id.action_notifications_to_tutorial)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_requested", notificationRequested)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
