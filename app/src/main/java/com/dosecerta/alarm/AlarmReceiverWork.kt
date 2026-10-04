package com.dosecerta.alarm

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Receiver work has a bounded lifetime; every path releases the framework's pending result. */
internal fun BroadcastReceiver.runAlarmWork(context: Context, id: String? = null, work: suspend () -> Unit) {
    val pending = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
        try { withTimeout(9000) { work() } }
        catch (error: Exception) { AlarmDiagnostics.record(context, "receiver_failure", id, error.javaClass.simpleName) }
        finally { pending.finish(); scope.cancel() }
    }
}
