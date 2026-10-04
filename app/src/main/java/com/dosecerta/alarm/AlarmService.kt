package com.dosecerta.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.MedicationLog
import com.dosecerta.data.repository.MedicationRepository
import com.dosecerta.domain.DoseActionCoordinator
import com.dosecerta.domain.DosePolicy
import com.dosecerta.domain.DoseState
import com.dosecerta.notification.NotificationHelper
import com.dosecerta.util.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One media owner, a queue of independent occurrences, and bounded resources. */
class AlarmService : Service() {
    companion object {
        const val CHANNEL_ID = ReminderChannels.ALARM
        const val FOREGROUND_NOTIFICATION_ID = 1001
        private var running: AlarmService? = null
        private val main = Handler(Looper.getMainLooper())

        fun startOccurrence(context: Context, id: String): Boolean = try {
            context.startForegroundService(AlarmIdentity.intent(context, AlarmService::class.java, id, "service"))
            AlarmDiagnostics.record(context, "service_start", id, "requested")
            true
        } catch (error: RuntimeException) {
            AlarmDiagnostics.record(context, "service_start", id, error.javaClass.simpleName)
            false
        }

        /** No background service is created just to stop a different alarm. */
        fun stopAlarm(context: Context, id: String) {
            main.post { running?.removeOccurrence(id) }
            NotificationHelper(context).cancel(id)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { MedicationRepository(DoseCertaDatabase.getDatabase(this)) }
    private val queue = linkedMapOf<String, MedicationLog>()
    private val loads = mutableMapOf<String, Job>()
    private val sound = AlarmSoundManager()
    private var current: String? = null
    private var soundTimer: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alive = false

    override fun onCreate() {
        super.onCreate()
        alive = true
        running = this
        ReminderChannels.ensure(this)
        scope.launch {
            repository.getAllLogs().collect { logs ->
                val byId = logs.associateBy { it.occurrenceId }
                (queue.keys + loads.keys).toSet().forEach { id ->
                    val state = byId[id]?.state
                    if (state != DoseState.ALERTING) removeOccurrence(id)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(AlarmIdentity.EXTRA_OCCURRENCE_ID)
        if (id == null || !ReminderCapabilityChecker(this).check().canNotifyAlarm) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        // Only a generic silent placeholder is published before validation; no audio precedes IO.
        try { startForeground(FOREGROUND_NOTIFICATION_ID, NotificationHelper(this).foregroundPlaceholder()) }
        catch (error: RuntimeException) {
            AlarmDiagnostics.record(this, "foreground", id, error.javaClass.simpleName)
            scope.launch {
                val occurrence = withContext(Dispatchers.IO) { repository.getOccurrence(id) }
                if (occurrence != null) NotificationHelper(this@AlarmService).showAlarm(occurrence, false, false)
                stopSelf(startId)
            }
            return START_NOT_STICKY
        }
        if (id in queue || id in loads) return START_NOT_STICKY
        val loading = scope.launch {
            try {
                val occurrence = withContext(Dispatchers.IO) {
                    repository.getOccurrence(id)?.takeIf {
                        it.state == DoseState.ALERTING && it.deadlineAt > System.currentTimeMillis() && repository.isOccurrenceCurrent(it)
                    }
                }
                if (!alive || occurrence == null || !ReminderCapabilityChecker(this@AlarmService).check().canNotifyAlarm) {
                    AlarmDiagnostics.record(this@AlarmService, "service_validate", id, "stale_or_blocked")
                    return@launch
                }
                queue[id] = occurrence
                NotificationHelper(this@AlarmService).showAlarm(occurrence, true, true)
                AlarmDiagnostics.record(this@AlarmService, "service_validate", id, "accepted")
                playNext()
            } finally {
                loads.remove(id)
                if (queue.isEmpty() && loads.isEmpty()) stopSelf()
            }
        }
        loads[id] = loading
        return START_NOT_STICKY
    }

    private fun playNext() {
        if (!alive || current != null) return
        val occurrence = queue.values.firstOrNull() ?: run { if (loads.isEmpty()) stopSelf(); return }
        current = occurrence.occurrenceId
        scope.launch {
            val id = occurrence.occurrenceId
            val uri = withContext(Dispatchers.IO) { SettingsPreferences(this@AlarmService).getAlarmSoundUriSync() }
            val valid = withContext(Dispatchers.IO) {
                repository.getOccurrence(id)?.let { it.state == DoseState.ALERTING && it.deadlineAt > System.currentTimeMillis() && repository.isOccurrenceCurrent(it) } == true
            }
            if (!alive || current != id || !valid || !ReminderCapabilityChecker(this@AlarmService).check().canNotifyAlarm) { removeOccurrence(id); return@launch }
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DoseCerta:AlarmAudio").apply {
                acquire(DosePolicy.MAX_SOUND_MILLIS + 5000)
            }
            sound.start(this@AlarmService, uri?.let(android.net.Uri::parse)) { AlarmDiagnostics.record(this@AlarmService, "audio", id, "playing") }
            AlarmDiagnostics.record(this@AlarmService, "audio", id, "prepare_requested")
            soundTimer?.cancel()
            soundTimer = scope.launch {
                delay(minOf(DosePolicy.MAX_SOUND_MILLIS, occurrence.deadlineAt - System.currentTimeMillis()).coerceAtLeast(1))
                withContext(Dispatchers.IO) { DoseActionCoordinator(repository).dismiss(id) }
                if (current == id) removeOccurrence(id)
            }
        }
    }

    private fun removeOccurrence(id: String) {
        loads.remove(id)?.cancel()
        queue.remove(id)
        NotificationHelper(this).cancel(id)
        if (current == id) {
            current = null
            soundTimer?.cancel(); soundTimer = null
            sound.release()
            wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
            AlarmDiagnostics.record(this, "audio", id, "stopped")
        }
        if (alive) { playNext(); if (queue.isEmpty() && loads.isEmpty()) stopSelf() }
    }

    override fun onDestroy() {
        alive = false
        if (running === this) running = null
        scope.cancel()
        sound.release()
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        queue.keys.forEach { NotificationHelper(this).cancel(it) }
        queue.clear(); loads.clear(); current = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        AlarmDiagnostics.record(this, "service_destroy")
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
