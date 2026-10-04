package com.dosecerta.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
import com.dosecerta.util.SettingsPreferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var identityVisible = false
    private var observer: Job? = null
    private var action: Job? = null
    private var dialog: androidx.appcompat.app.AlertDialog? = null

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
        binding.buttonTake.setOnClickListener { confirmOutcome(take = true) }
        binding.swipeTake.onConfirmed = { confirmOutcome(take = true) }
        binding.buttonSkip.setOnClickListener { confirmOutcome(take = false) }
        binding.buttonSnooze.setOnClickListener { chooseSnooze() }
        binding.buttonSilence.setOnClickListener { silence() }
        binding.buttonUnlock.setOnClickListener { unlock() }
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
    override fun onResume() { super.onResume(); occurrence?.let { lifecycleScope.launch { render(it) } } }

    private fun switchOccurrence(id: String?) {
        generation++
        observer?.cancel(); action?.cancel(); dialog?.dismiss(); dialog = null
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

    private suspend fun render(log: MedicationLog) {
        val token = generation
        val detailsOnLock = SettingsPreferences(this).getShowMedicationOnLockScreenSync()
        if (token != generation) return
        identityVisible = detailsOnLock || !getSystemService(KeyguardManager::class.java).isKeyguardLocked
        binding.buttonUnlock.visibility = if (identityVisible) View.GONE else View.VISIBLE
        binding.layoutIdentityActions.visibility = if (identityVisible) View.VISIBLE else View.GONE
        binding.textMedicationName.text = if (identityVisible) log.snapshotName ?: getString(R.string.reminder_private_title) else getString(R.string.reminder_private_title)
        binding.textDosageInfo.text = if (identityVisible) listOfNotNull(log.snapshotDosage, log.snapshotUnit).joinToString(" ") else getString(R.string.reminder_private_message)
        binding.textScheduledTime.text = if (identityVisible) getString(R.string.reminder_scheduled_at, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(log.originalDueAt))) else ""
    }

    private fun confirmOutcome(take: Boolean) {
        val log = occurrence ?: return
        if (!identityVisible || action?.isActive == true) { binding.swipeTake.reset(); return }
        val token = generation
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (take) R.string.reminder_confirm_take else R.string.reminder_confirm_skip)
            .setMessage(getString(R.string.reminder_confirm_dose, log.snapshotName, listOfNotNull(log.snapshotDosage, log.snapshotUnit).joinToString(" ")))
            .setNegativeButton(R.string.cancel) { _, _ -> binding.swipeTake.reset() }
            .setPositiveButton(if (take) R.string.notification_action_take else R.string.notification_action_skip) { _, _ ->
                if (token == generation) perform { coordinator, id -> if (take) coordinator.take(id) else coordinator.skip(id) }
            }.setOnCancelListener { binding.swipeTake.reset() }.show()
    }

    private fun chooseSnooze() {
        if (occurrence == null || action?.isActive == true) return
        val token = generation
        val options = Constants.SNOOZE_OPTIONS_MINUTES
        val labels = options.map { getString(R.string.reminder_minutes, it) }.toTypedArray()
        var selected = options.indexOf(10)
        dialog?.dismiss()
        dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.reminder_snooze)
            .setSingleChoiceItems(labels, selected) { _, index -> selected = index }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.reminder_snooze) { _, _ ->
                if (token == generation) perform { _, id -> AlarmScheduler(this).snoozeOccurrence(id, options[selected]) }
            }.show()
    }

    private fun silence() { perform { coordinator, id -> coordinator.dismiss(id) } }
    private fun perform(operation: suspend (DoseActionCoordinator, String) -> DoseActionResult) {
        val id = occurrenceId ?: return
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
    private fun unlock() {
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { occurrence?.let { lifecycleScope.launch { render(it) } } }
            override fun onDismissCancelled() { binding.textActionError.setText(R.string.reminder_unlock_cancelled); binding.textActionError.visibility = View.VISIBLE }
            override fun onDismissError() { binding.textActionError.setText(R.string.reminder_unlock_cancelled); binding.textActionError.visibility = View.VISIBLE }
        })
    }
    override fun onDestroy() { generation++; dialog?.dismiss(); binding.swipeTake.reset(); super.onDestroy() }
}
