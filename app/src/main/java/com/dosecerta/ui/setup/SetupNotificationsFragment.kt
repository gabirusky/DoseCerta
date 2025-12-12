package com.dosecerta.ui.setup

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.dosecerta.R
import com.dosecerta.databinding.FragmentSetupNotificationsBinding
import com.dosecerta.ui.MainActivity

/**
 * Step 2 of setup: Request notification permission.
 * Also handles full-screen intent permission for Android 14+.
 * After this step, go directly to MainActivity (tutorial shown as overlay there).
 */
class SetupNotificationsFragment : Fragment() {
    
    private var _binding: FragmentSetupNotificationsBinding? = null
    private val binding get() = _binding!!
    
    // Flag to track if we need to check full-screen intent permission after returning from settings
    private var pendingFullScreenIntentCheck = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showPermissionGranted()
            // On Android 14+, also request full-screen intent permission
            checkAndRequestFullScreenIntentPermission()
        } else {
            // Even if denied, still check full-screen intent permission
            checkAndRequestFullScreenIntentPermission()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        checkExistingPermission()
        setupButtons()
    }
    
    private fun checkExistingPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                showPermissionGranted()
            }
        } else {
            // Pre-Android 13: Notifications are enabled by default
            showPermissionGranted()
        }
    }
    
    private fun showPermissionGranted() {
        binding.layoutPermissionGranted.visibility = View.VISIBLE
        binding.buttonAllow.text = getString(R.string.setup_notifications_continue)
    }
    
    private fun setupButtons() {
        binding.buttonAllow.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Permission already granted, check full-screen intent
                    checkAndRequestFullScreenIntentPermission()
                }
            } else {
                // Pre-Android 13, check full-screen intent permission
                checkAndRequestFullScreenIntentPermission()
            }
        }
        
        binding.buttonSkip.setOnClickListener {
            navigateToMainApp()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // If returning from full-screen intent settings, navigate to main app
        if (pendingFullScreenIntentCheck) {
            pendingFullScreenIntentCheck = false
            navigateToMainApp()
        }
    }
    
    /**
     * Check if full-screen intent permission is granted on Android 14+.
     * If not, open system settings to let user enable it.
     */
    private fun checkAndRequestFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                // Permission not granted, open settings
                pendingFullScreenIntentCheck = true
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to app notification settings if specific intent fails
                    try {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        }
                        startActivity(intent)
                    } catch (e2: Exception) {
                        // If all else fails, just navigate to main app
                        pendingFullScreenIntentCheck = false
                        navigateToMainApp()
                    }
                }
                return
            }
        }
        // Permission granted or not needed, navigate to main app
        navigateToMainApp()
    }
    
    private fun navigateToMainApp() {
        // Go directly to MainActivity, tutorial will be shown as overlay there
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
