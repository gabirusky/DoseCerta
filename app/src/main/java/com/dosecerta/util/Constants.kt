package com.dosecerta.util

object Constants {
    
    // Notification
    const val NOTIFICATION_CHANNEL_ID = "medication_reminders"
    const val NOTIFICATION_CHANNEL_NAME = "Lembretes de Medicação"
    
    // Intent Actions
    const val ACTION_TAKE_MEDICATION = "com.dosecerta.ACTION_TAKE"
    const val ACTION_SKIP_MEDICATION = "com.dosecerta.ACTION_SKIP"
    const val ACTION_SNOOZE_MEDICATION = "com.dosecerta.ACTION_SNOOZE"
    
    // Intent Extras
    const val EXTRA_MEDICATION_ID = "medication_id"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_SCHEDULED_TIME = "scheduled_time"
    
    // Snooze duration
    const val SNOOZE_DURATION_MINUTES = 10
    
    // Time ranges for history
    const val DAYS_IN_WEEK = 7
    const val DAYS_IN_MONTH = 30
}
