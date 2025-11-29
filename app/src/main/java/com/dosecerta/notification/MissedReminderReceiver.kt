package com.dosecerta.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.util.Constants
import kotlinx.coroutines.runBlocking

/**
 * Receiver for showing reminder notification 2 hours after missing medication.
 */
class MissedReminderReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "MissedReminderReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive called - showing missed medication reminder")
        
        val medicationId = intent.getLongExtra(Constants.EXTRA_MEDICATION_ID, -1L)
        val scheduleId = intent.getLongExtra(Constants.EXTRA_SCHEDULE_ID, -1L)
        
        if (medicationId == -1L || scheduleId == -1L) {
            Log.e(TAG, "Invalid IDs")
            return
        }
        
        runBlocking {
            try {
                val database = DoseCertaDatabase.getDatabase(context)
                val medication = database.medicationDao().getMedicationByIdSync(medicationId)
                
                if (medication != null) {
                    // Show reminder notification
                    val notificationHelper = NotificationHelper(context)
                    notificationHelper.showMissedReminderNotification(medication.name)
                    Log.d(TAG, "Showed missed reminder for: ${medication.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing reminder", e)
                e.printStackTrace()
            }
        }
    }
}
