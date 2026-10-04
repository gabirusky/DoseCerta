package com.dosecerta.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.dosecerta.R
import com.dosecerta.alarm.AlarmActivity
import com.dosecerta.alarm.AlarmDiagnostics
import com.dosecerta.alarm.AlarmIdentity
import com.dosecerta.alarm.ReminderCapabilityChecker
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseState
import com.dosecerta.databinding.ActivityMainBinding
import com.dosecerta.ui.setup.SetupActivity
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val presentedAlarms = mutableSetOf<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presentedAlarms.addAll(savedInstanceState?.getStringArrayList("presented_alarms").orEmpty())
        lifecycleScope.launch {
            if (!SettingsPreferences(this@MainActivity).isSetupCompletedSync()) {
                startActivity(Intent(this@MainActivity, SetupActivity::class.java))
                finish()
                return@launch
            }
            val binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            WindowInsetsHelper.apply(binding.root)
            val lightTheme = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK != android.content.res.Configuration.UI_MODE_NIGHT_YES
            androidx.core.view.WindowCompat.getInsetsController(window, binding.root).apply {
                isAppearanceLightStatusBars = lightTheme
                isAppearanceLightNavigationBars = lightTheme
            }
            val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            binding.bottomNavigation.setupWithNavController(host.navController)
            host.navController.addOnDestinationChangedListener { _, destination, _ ->
                binding.bottomNavigation.visibility = if (destination.id in setOf(R.id.nav_add_medication, R.id.nav_privacy_policy)) android.view.View.GONE else android.view.View.VISIBLE
            }
            observeForegroundAlarms()
        }
    }

    /** A visible app can open its card directly; background presentation belongs to SystemUI. */
    private fun observeForegroundAlarms() {
        val repository = MedicationRepository(DoseCertaDatabase.getDatabase(this))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                repository.getAllLogs().collect { logs ->
                    val now = System.currentTimeMillis()
                    val alarm = logs.filter {
                        it.state == DoseState.ALERTING && it.deliveredAt != null && it.deadlineAt > now && it.occurrenceId !in presentedAlarms
                    }.sortedBy { it.originalDueAt }.firstOrNull { withContext(Dispatchers.IO) { repository.isOccurrenceCurrent(it) } }
                    if (alarm != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ReminderCapabilityChecker(this@MainActivity).check().canNotifyAlarm) {
                        presentedAlarms += alarm.occurrenceId
                        startActivity(AlarmIdentity.intent(this@MainActivity, AlarmActivity::class.java, alarm.occurrenceId, "card"))
                        AlarmDiagnostics.record(this@MainActivity, "card_foreground", alarm.occurrenceId, "requested")
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("presented_alarms", ArrayList(presentedAlarms))
        super.onSaveInstanceState(outState)
    }
}
