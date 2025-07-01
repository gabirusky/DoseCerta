package com.example.dosecerta.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.dosecerta.DoseCertaApplication
import com.example.dosecerta.data.model.Reminder
import com.example.dosecerta.util.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device boot completed, rescheduling reminders.")
            val repository = (context.applicationContext as DoseCertaApplication).repository
            
            scope.launch {
                try {
                    val allEnabledReminders: List<Reminder> = repository.getAllEnabledReminders()
                    if (allEnabledReminders.isNotEmpty()) {
                        // Group reminders by medication ID
                        val remindersByMedId = allEnabledReminders.groupBy { it.medicationId }

                        for ((medId, remindersForMed) in remindersByMedId) {
                            val medication = repository.getMedicationById(medId)
                            if (medication != null) {
                                // Now schedule with medication details
                                ReminderScheduler.scheduleReminders(context, medication, remindersForMed)
                                Log.d(TAG, "Rescheduled ${remindersForMed.size} reminders for med ID $medId after boot.")
                            } else {
                                Log.w(TAG, "Could not find medication for ID $medId during boot reschedule, skipping ${remindersForMed.size} reminders.")
                            }
                        }
                    } else {
                         Log.d(TAG, "No enabled reminders found to reschedule after boot.")
                    }
                } catch (e: Exception) {
                     Log.e(TAG, "Error rescheduling reminders after boot", e)
                }
            }
        }
    }
} 