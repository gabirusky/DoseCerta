package com.dosecerta.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.dosecerta.R
import com.dosecerta.databinding.ActivityMainBinding
import com.dosecerta.ui.setup.SetupActivity
import com.dosecerta.util.SettingsPreferences
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Data class for tutorial steps with icons.
 */
data class TutorialItem(
    val iconRes: Int,
    val titleRes: Int,
    val descriptionRes: Int
)

/**
 * Main activity hosting the bottom navigation and nav host fragment.
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var settingsPreferences: SettingsPreferences
    
    private var currentTutorialStep = 0
    private val tutorialItems = listOf(
        TutorialItem(
            iconRes = R.drawable.ic_tutorial_overview,
            titleRes = R.string.setup_tutorial_title_1,
            descriptionRes = R.string.setup_tutorial_desc_1
        ),
        TutorialItem(
            iconRes = R.drawable.ic_tutorial_add,
            titleRes = R.string.setup_tutorial_title_2,
            descriptionRes = R.string.setup_tutorial_desc_2
        ),
        TutorialItem(
            iconRes = R.drawable.ic_tutorial_reminder,
            titleRes = R.string.setup_tutorial_title_3,
            descriptionRes = R.string.setup_tutorial_desc_3
        )
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission is granted
        } else {
            // Permission denied
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsPreferences = SettingsPreferences(this)
        
        // Check if terms have been accepted (first part of setup)
        lifecycleScope.launch {
            val hasAcceptedTerms = settingsPreferences.hasAcceptedTermsSync()
            
            if (!hasAcceptedTerms) {
                // Redirect to setup activity for terms & notifications
                val intent = Intent(this@MainActivity, SetupActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                return@launch
            }
            
            // Terms accepted, initialize main activity
            initializeMainActivity()
            
            // Check if tutorial should be shown (after setup but first time in main app)
            val isTutorialCompleted = settingsPreferences.isSetupCompletedSync()
            if (!isTutorialCompleted) {
                // Wait for UI to load, then show tutorial
                delay(500)
                showTutorialOverlay()
            }
        }
    }
    
    private fun initializeMainActivity() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupNavigation()
        checkPermissions()
    }
    
    private fun showTutorialOverlay() {
        val overlay = binding.tutorialOverlay
        overlay.root.visibility = View.VISIBLE
        overlay.root.alpha = 0f
        
        // Fade in animation
        overlay.root.animate()
            .alpha(1f)
            .setDuration(400)
            .withStartAction { overlay.root.visibility = View.VISIBLE }
            .start()
        
        // Setup tutorial content
        setupTutorialViewPager()
        setupTutorialButton()
    }
    
    private fun setupTutorialViewPager() {
        val overlay = binding.tutorialOverlay
        val adapter = TutorialAdapter(tutorialItems)
        overlay.tutorialViewPager.adapter = adapter
        
        overlay.tutorialViewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentTutorialStep = position
                updateIndicators(position)
                updateButtonState(position)
            }
        })
    }
    
    private fun updateIndicators(position: Int) {
        val overlay = binding.tutorialOverlay
        val indicators = listOf(
            overlay.indicator1,
            overlay.indicator2,
            overlay.indicator3
        )
        
        indicators.forEachIndexed { index, indicator ->
            indicator.setBackgroundResource(
                if (index == position) {
                    R.drawable.indicator_dot_white_selected
                } else {
                    R.drawable.indicator_dot_white_unselected
                }
            )
        }
    }
    
    private fun updateButtonState(position: Int) {
        val overlay = binding.tutorialOverlay
        val isLastStep = position == tutorialItems.size - 1
        overlay.tutorialButton.text = 
            if (isLastStep) getString(R.string.setup_tutorial_button_start) 
            else getString(R.string.setup_tutorial_button_next)
    }
    
    // Legacy method no longer used directly, logic moved to OnPageChangeCallback
    private fun updateTutorialContent() {
        // Kept empty if called elsewhere, but we replaced calls in showTutorialOverlay
    }
    
    private fun setupTutorialButton() {
        val overlay = binding.tutorialOverlay
        overlay.tutorialButton.setOnClickListener {
            val viewPager = overlay.tutorialViewPager
            val currentItem = viewPager.currentItem
            val isLastStep = currentItem == tutorialItems.size - 1
            
            if (isLastStep) {
                // Complete tutorial
                hideTutorialOverlay()
                lifecycleScope.launch {
                    settingsPreferences.setSetupCompleted()
                }
            } else {
                // Next step
                viewPager.currentItem = currentItem + 1
            }
        }
    }
    
    private fun hideTutorialOverlay() {
        val overlay = binding.tutorialOverlay
        
        overlay.root.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { 
                overlay.root.visibility = View.GONE 
            }
            .start()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmPermissionDialog()
            }
        }
    }

    private fun showExactAlarmPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exact_alarm_permission_title)
            .setMessage(R.string.exact_alarm_permission_message)
            .setPositiveButton(R.string.settings) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

class TutorialAdapter(private val items: List<TutorialItem>) : 
    androidx.recyclerview.widget.RecyclerView.Adapter<TutorialAdapter.TutorialViewHolder>() {

    class TutorialViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.tutorial_page_icon)
        val title: TextView = view.findViewById(R.id.tutorial_page_title)
        val description: TextView = view.findViewById(R.id.tutorial_page_description)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TutorialViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tutorial_page, parent, false)
        return TutorialViewHolder(view)
    }

    override fun onBindViewHolder(holder: TutorialViewHolder, position: Int) {
        val item = items[position]
        holder.icon.setImageResource(item.iconRes)
        holder.title.setText(item.titleRes)
        holder.description.setText(item.descriptionRes)
    }

    override fun getItemCount() = items.size
}
