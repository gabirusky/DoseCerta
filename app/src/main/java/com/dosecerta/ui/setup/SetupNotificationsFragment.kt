package com.dosecerta.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
 * After this step, go directly to MainActivity (tutorial shown as overlay there).
 */
class SetupNotificationsFragment : Fragment() {
    
    private var _binding: FragmentSetupNotificationsBinding? = null
    private val binding get() = _binding!!
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            showPermissionGranted()
        }
        // Navigate to main app (tutorial will be shown there)
        navigateToMainApp()
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
                    navigateToMainApp()
                }
            } else {
                navigateToMainApp()
            }
        }
        
        binding.buttonSkip.setOnClickListener {
            navigateToMainApp()
        }
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
