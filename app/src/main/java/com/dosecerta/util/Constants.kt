package com.dosecerta.util

object Constants {
    
    // Notification
    const val NOTIFICATION_CHANNEL_ID = "medication_reminders"
    const val NOTIFICATION_CHANNEL_NAME = "Lembretes de Medicação"
    
    // Intent Actions
    const val ACTION_TAKE_MEDICATION = "com.dosecerta.ACTION_TAKE"
    const val ACTION_SKIP_MEDICATION = "com.dosecerta.ACTION_SKIP"
    const val ACTION_SNOOZE_MEDICATION = "com.dosecerta.ACTION_SNOOZE"
    const val ACTION_MARK_MISSED = "com.dosecerta.ACTION_MARK_MISSED"
    
    // Intent Extras
    const val EXTRA_MEDICATION_ID = "medication_id"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_SCHEDULED_TIME = "scheduled_time"
    
    // Snooze duration
    const val SNOOZE_DURATION_MINUTES = 10
    
    // Missed check delay (mark as missed if no action after this time)
    const val MISSED_CHECK_DELAY_MINUTES = 30
    
    // Missed reminder delay (show reminder notification after missing)
    const val MISSED_REMINDER_DELAY_HOURS = 2
    
    // Time ranges for history
    const val DAYS_IN_WEEK = 7
    const val DAYS_IN_MONTH = 30
}
