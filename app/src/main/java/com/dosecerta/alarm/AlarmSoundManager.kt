package com.dosecerta.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

/**
 * Manages the MediaPlayer for alarm sounds.
 * Configured to play with USAGE_ALARM to bypass silent/DND modes.
 */
class AlarmSoundManager {
    
    companion object {
        private const val TAG = "AlarmSoundManager"
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    
    /**
     * Start playing the alarm sound.
     * @param context Application context
     * @param soundUri URI of the sound to play, or null for default system alarm
     */
    fun start(context: Context, soundUri: Uri?) {
        if (isPlaying) {
            Log.w(TAG, "Sound is already playing")
            return
        }
        
        try {
            // Release any existing player
            release()
            
            // Determine which sound to use
            val actualUri = soundUri ?: getDefaultAlarmSound(context)
            
            // Create and configure MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, actualUri)
                
                // Configure as ALARM to bypass silent/DND modes
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                
                setAudioAttributes(audioAttributes)
                isLooping = true // Loop infinitely like a real alarm
                
                // Prepare asynchronously
                setOnPreparedListener { mp ->
                    mp.start()
                    this@AlarmSoundManager.isPlaying = true
                    Log.d(TAG, "Alarm sound started playing")
                }
                
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                    // Try fallback to default sound
                    tryFallbackSound(context)
                    true // Error handled
                }
                
                prepareAsync()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm sound", e)
            tryFallbackSound(context)
        }
    }
    
    /**
     * Stop playing the alarm sound.
     */
    fun stop() {
        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.stop()
                    Log.d(TAG, "Alarm sound stopped")
                }
            }
            isPlaying = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping alarm sound", e)
        }
    }
    
    /**
     * Release all resources.
     */
    fun release() {
        try {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
            Log.d(TAG, "MediaPlayer released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        }
    }
    
    /**
     * Get the default system alarm sound.
     */
    private fun getDefaultAlarmSound(context: Context): Uri {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Uri.parse("android.resource://${context.packageName}/raw/default_alarm")
    }
    
    /**
     * Try to play fallback sound on error.
     */
    private fun tryFallbackSound(context: Context) {
        try {
            release()
            val fallbackUri = getDefaultAlarmSound(context)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, fallbackUri)
                
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                
                setAudioAttributes(audioAttributes)
                isLooping = true
                prepare()
                start()
                this@AlarmSoundManager.isPlaying = true
                Log.d(TAG, "Fallback sound started")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start fallback sound", e)
        }
    }
}
