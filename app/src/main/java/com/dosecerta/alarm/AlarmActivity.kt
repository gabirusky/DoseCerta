package com.dosecerta.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dosecerta.R
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.databinding.ActivityAlarmBinding
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DoseActionResult
import com.dosecerta.domain.DoseState
import com.dosecerta.ui.WindowInsetsHelper
import com.dosecerta.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Reloads persisted identity on every intent/restoration; actions never trust medicine extras. */
class AlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmBinding
    private val repository by lazy { MedicationRepository(DoseCertaDatabase.getDatabase(this)) }
    private var occurrenceId: String? = null
    private var occurrence: MedicationLog? = null
    private var generation = 0L
    private var observer: Job? = null
    private var action: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true); setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowInsetsHelper.apply(binding.root)
        binding.buttonTake.setOnClickListener { take() }
        binding.swipeTake.onConfirmed = { take() }
        binding.buttonSkip.setOnClickListener { skip() }
        binding.buttonSnooze.setOnClickListener { snooze() }
        binding.buttonSilence.setOnClickListener { silence() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = silence() })
        switchOccurrence(savedInstanceState?.getString(AlarmIdentity.EXTRA_OCCURRENCE_ID) ?: intent.getStringExtra(AlarmIdentity.EXTRA_OCCURRENCE_ID))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        switchOccurrence(intent.getStringExtra(AlarmIdentity.EXTRA_OCCURRENCE_ID))
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(AlarmIdentity.EXTRA_OCCURRENCE_ID, occurrenceId)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); occurrence?.let { render(it) } }

    private fun switchOccurrence(id: String?) {
        generation++
        observer?.cancel(); action?.cancel()
        binding.swipeTake.reset(); occurrence = null; occurrenceId = id
        binding.textActionError.visibility = View.GONE
        setBusy(true)
        if (id == null) { finish(); return }
        AlarmDiagnostics.record(this, "card", id, "opened")
        val token = generation
        observer = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getAllLogs().collect { logs ->
                    val log = logs.firstOrNull { it.occurrenceId == id }
                    val valid = withContext(Dispatchers.IO) { log != null && repository.isOccurrenceCurrent(log) }
                    if (token != generation) return@collect
                    if (!valid || log == null || log.state !in setOf(DoseState.PENDING, DoseState.ALERTING, DoseState.DISMISSED)) {
                        AlarmService.stopAlarm(this@AlarmActivity, id)
                        finish()
                    } else { occurrence = log; render(log); setBusy(action?.isActive == true) }
                }
            }
        }
    }

    private fun render(log: MedicationLog) {
        binding.textMedicationName.text = log.snapshotName ?: getString(R.string.reminder_private_title)
        binding.textMedicationName.setTextColor(log.snapshotColor ?: ContextCompat.getColor(this, R.color.ui_primary))
        binding.textDosageInfo.text = listOfNotNull(log.snapshotDosage, log.snapshotUnit).joinToString(" ")
        binding.textScheduledTime.text = getString(R.string.reminder_scheduled_at,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(log.originalDueAt)))
    }

    private fun take() { perform { coordinator, id -> coordinator.take(id) } }
    private fun skip() { perform { coordinator, id -> coordinator.skip(id) } }
    private fun snooze() { perform { _, id -> AlarmScheduler(this).snoozeOccurrence(id, Constants.SNOOZE_DURATION_MINUTES) } }
    private fun silence() { perform { coordinator, id -> coordinator.dismiss(id) } }
    private fun perform(operation: suspend (DoseActionCoordinator, String) -> DoseActionResult) {
        val id = occurrence?.occurrenceId ?: return
        if (action?.isActive == true) return
        val token = generation
        setBusy(true)
        action = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val result = operation(DoseActionCoordinator(repository), id)
                if (result is DoseActionResult.Success) {
                    if (result.occurrence.state in setOf(DoseState.TAKEN, DoseState.SKIPPED)) AlarmScheduler(this@AlarmActivity).cancelOccurrence(id)
                    else AlarmService.stopAlarm(this@AlarmActivity, id)
                }
                result
            }
            if (token != generation) return@launch
            when (result) {
                is DoseActionResult.Success -> finish()
                is DoseActionResult.Rejected -> {
                    binding.textActionError.setText(R.string.reminder_action_expired)
                    binding.textActionError.visibility = View.VISIBLE
                    setBusy(false); binding.swipeTake.reset()
                }
                is DoseActionResult.Failure -> {
                    binding.textActionError.setText(R.string.reminder_action_failed)
                    binding.textActionError.visibility = View.VISIBLE
                    setBusy(false); binding.swipeTake.reset()
                }
            }
        }
    }
    private fun setBusy(busy: Boolean) {
        listOf(binding.buttonTake, binding.buttonSkip, binding.buttonSnooze, binding.buttonSilence, binding.swipeTake).forEach { it.isEnabled = !busy }
    }
    override fun onDestroy() { generation++; binding.swipeTake.reset(); super.onDestroy() }
}
