package com.dosecerta

import android.app.Application
import com.dosecerta.alarm.AlarmDiagnostics
import com.dosecerta.alarm.AlarmScheduler
import com.dosecerta.alarm.ReminderChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DoseCertaApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        ReminderChannels.ensure(this)
        scope.launch {
            try { AlarmScheduler(this@DoseCertaApplication).reconcile() }
            catch (error: Exception) { AlarmDiagnostics.record(this@DoseCertaApplication, "startup_reconcile", result = error.javaClass.simpleName) }
        }
    }
}
