package com.dosecerta.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri

/** USAGE_ALARM follows the device alarm volume and DND policy. The service sets a finite lifetime. */
class AlarmSoundManager {
    private var player: MediaPlayer? = null
    private var generation = 0L

    fun start(context: Context, requested: Uri?, onStarted: (() -> Unit)? = null) {
        release()
        val token = generation
        val default = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        prepare(context, requested ?: default, default, token, requested != null, onStarted)
    }

    private fun prepare(context: Context, uri: Uri, fallback: Uri, token: Long, mayFallback: Boolean, onStarted: (() -> Unit)?) {
        if (token != generation) return
        val media = MediaPlayer()
        player = media
        try {
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            media.setDataSource(context, uri)
            media.isLooping = true
            media.setOnPreparedListener { prepared ->
                if (token == generation && player === prepared) { prepared.start(); onStarted?.invoke() }
            }
            media.setOnErrorListener { failed, _, _ ->
                if (token == generation && player === failed) {
                    failed.release(); player = null
                    if (mayFallback) prepare(context, fallback, fallback, token, false, onStarted)
                    else AlarmDiagnostics.record(context, "audio", result = "media_error")
                }
                true
            }
            media.prepareAsync()
        } catch (error: Exception) {
            media.release()
            if (player === media) player = null
            if (mayFallback && token == generation) prepare(context, fallback, fallback, token, false, onStarted)
            else AlarmDiagnostics.record(context, "audio", result = error.javaClass.simpleName)
        }
    }
    fun stop() = release()
    fun release() {
        generation++
        player?.let { media -> media.setOnPreparedListener(null); media.setOnErrorListener(null); media.release() }
        player = null
    }
}
