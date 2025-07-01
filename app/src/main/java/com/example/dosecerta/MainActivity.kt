package com.example.dosecerta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import com.example.dosecerta.databinding.ActivityMainBinding
import com.example.dosecerta.util.BugFixValidation
import com.example.dosecerta.util.LanguageManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import com.example.dosecerta.BuildConfig

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Declare the launcher at the top of your Activity/Fragment:
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted. Continue the action or workflow in your app.
            // You might not need to do anything specific here if the app functions
            // correctly once permission is granted.
        } else {
            // Explain to the user that the feature is unavailable because the
            // feature requires a permission that the user has denied. At the
            // same time, respect the user's decision. Don't link to system
            // settings in an effort to convince the user to change their
            // decision.
            Snackbar.make(binding.root, getString(R.string.notification_permission_denied), Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply language before setting content view
        LanguageManager.initializeLanguage(this)
        
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup Bottom Navigation View with NavController
        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        // We won't need an AppBarConfiguration here as we won't have a top AppBar
        navView.setupWithNavController(navController)

        // Remove default action bar/toolbar
        supportActionBar?.hide()
        
        // Ask for notification permission
        askNotificationPermission()
        
        // Run validation in debug builds
        if (BuildConfig.DEBUG) {
            runBugFixValidation()
        }
    }
    
    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // Permission already granted
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: Display an educational UI explaining why the permission is needed.
                // For now, we'll just request it again, but this should be improved.
                 Snackbar.make(
                    binding.root, 
                    getString(R.string.notification_permission_needed), 
                    Snackbar.LENGTH_INDEFINITE
                 ).setAction(getString(R.string.ok)) { 
                     requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                 }.show()
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    
    private fun runBugFixValidation() {
        val repository = (application as DoseCertaApplication).repository
        
        BugFixValidation.validateAllBugFixes(this, repository) { result ->
            runOnUiThread {
                val message = if (result.allTestsPassed) {
                    getString(R.string.all_bug_fixes_validated)
                } else {
                    getString(R.string.validation_issues_detected)
                }
                
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction("Details") {
                        // Log detailed results
                        android.util.Log.i("MainActivity", "Validation Summary: ${result.summary}")
                        result.results.forEach { 
                            android.util.Log.i("MainActivity", "  $it") 
                        }
                    }
                    .show()
            }
        }
    }
}