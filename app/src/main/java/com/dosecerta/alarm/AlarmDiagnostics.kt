package com.dosecerta.alarm

import android.content.Context
import android.util.Log
import java.security.MessageDigest

/** Bounded local diagnostic trail. Never records medicine names, dose values or exception messages. */
object AlarmDiagnostics {
    @Synchronized
    fun record(context: Context, stage: String, id: String? = null, result: String = "ok") {
        val opaque = id?.let { value -> MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .take(6).joinToString("") { "%02x".format(it) } } ?: "-"
        val line = "${System.currentTimeMillis()} $stage $opaque $result"
        val preferences = context.getSharedPreferences("alarm_diagnostics", Context.MODE_PRIVATE)
        val lines = (preferences.getString("events", "").orEmpty().lines().filter { it.isNotBlank() } + line).takeLast(100)
        preferences.edit().putString("events", lines.joinToString("\n")).apply()
        Log.i("DoseCertaAlarm", "$stage $opaque $result")
    }

    fun read(context: Context): String = context.getSharedPreferences("alarm_diagnostics", Context.MODE_PRIVATE)
        .getString("events", "").orEmpty()
}
