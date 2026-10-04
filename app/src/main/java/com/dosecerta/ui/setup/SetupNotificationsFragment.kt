package com.dosecerta.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.dosecerta.alarm.ReminderSettingsNavigator
import com.dosecerta.databinding.FragmentSetupNotificationsBinding

/** Grants are independent explicit choices. Returning from Settings only refreshes effective state. */
class SetupNotificationsFragment : Fragment() {
    private var _binding: FragmentSetupNotificationsBinding? = null
    private val binding get() = _binding!!
    private var requested = false
    private var requesting = false
    private val requestNotification = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        requesting = false
        if (_binding != null) refresh()
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSetupNotificationsBinding.inflate(inflater, container, false)
        requested = savedInstanceState?.getBoolean("notification_requested") ?: false
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.buttonAllow.setOnClickListener {
            if (requesting) return@setOnClickListener
            val checker = ReminderCapabilityChecker(requireContext())
            val state = checker.check()
            if (state.canNotifyAlarm) next()
            else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && (!requested || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                requested = true; requesting = true; refresh()
                requestNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else open(checker.notificationsSettings(if (state.notificationPermission && state.notificationsEnabled) ReminderChannels.ALARM else null))
        }
        binding.buttonSkip.setOnClickListener { if (!requesting) next() }
        binding.buttonExact.setOnClickListener { if (!requesting) open(ReminderCapabilityChecker(requireContext()).exactAlarmSettings()) }
        binding.buttonFullScreen.setOnClickListener { if (!requesting) open(ReminderCapabilityChecker(requireContext()).fullScreenSettings()) }
        refresh()
    }
    override fun onResume() { super.onResume(); if (_binding != null) refresh() }
    private fun refresh() {
        val state = ReminderCapabilityChecker(requireContext()).check()
        binding.textCapabilityStatus.text = listOf(
            getString(if (state.canNotifyAlarm) R.string.reminder_notifications_ready else R.string.reminder_notifications_blocked),
            getString(if (state.exactAlarms) R.string.reminder_exact_ready else R.string.reminder_exact_degraded),
            getString(if (state.fullScreenIntent) R.string.reminder_fullscreen_ready else R.string.reminder_fullscreen_blocked)
        ).joinToString("\n\n")
        binding.buttonAllow.setText(if (state.canNotifyAlarm) R.string.setup_notifications_continue else R.string.setup_notifications_button)
        listOf(binding.buttonAllow, binding.buttonSkip, binding.buttonExact, binding.buttonFullScreen).forEach { it.isEnabled = !requesting }
        binding.buttonExact.visibility = if (Build.VERSION.SDK_INT >= 31) View.VISIBLE else View.GONE
        binding.buttonFullScreen.visibility = if (Build.VERSION.SDK_INT >= 34) View.VISIBLE else View.GONE
    }
    private fun open(intent: android.content.Intent) {
        if (!ReminderSettingsNavigator.open(this, intent)) Toast.makeText(requireContext(), R.string.reminder_settings_unavailable, Toast.LENGTH_LONG).show()
    }
    private fun next() {
        if (findNavController().currentDestination?.id == R.id.setupNotificationsFragment) findNavController().navigate(R.id.action_notifications_to_tutorial)
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("notification_requested", requested); super.onSaveInstanceState(outState) }
    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
