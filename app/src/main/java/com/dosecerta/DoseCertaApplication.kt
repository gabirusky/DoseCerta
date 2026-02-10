package com.dosecerta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dosecerta.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application class for Dose Certa.
 */
class DoseCertaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Schedule alarms for existing medications on app startup (e.g., after reboot)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = com.dosecerta.data.local.DoseCertaDatabase.getDatabase(this@DoseCertaApplication)
                val schedules = database.scheduleDao().getAllActiveSchedulesSync()
                
                val alarmScheduler = com.dosecerta.alarm.AlarmScheduler(this@DoseCertaApplication)
                schedules.forEach { schedule ->
                    alarmScheduler.scheduleAlarm(schedule.medicationId, schedule)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = Constants.NOTIFICATION_CHANNEL_NAME
            val descriptionText = "Alertas para tomar seus medicamentos no horário programado"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                name,
                importance
            ).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
