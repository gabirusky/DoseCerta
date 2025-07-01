package com.example.dosecerta

import android.app.Application
import com.example.dosecerta.data.db.AppDatabase
import com.example.dosecerta.data.repository.MedicationRepository
import com.example.dosecerta.util.DatabaseCleanupUtil
import com.example.dosecerta.util.LanguageManager
import com.example.dosecerta.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class DoseCertaApplication : Application() {
    // No need to cancel this scope as it'll be torn down with the process
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    private val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { MedicationRepository(database.medicationDao(), database.reminderDao(), database.medicationLogDao()) }

    override fun onCreate() {
        super.onCreate()
        // Initialize language settings
        LanguageManager.initializeLanguage(this)
        // Create the notification channel as soon as the app starts
        NotificationHelper.createNotificationChannel(this)
        
        // Perform database cleanup to fix existing duplicate logs
        DatabaseCleanupUtil.performFullCleanup(repository, applicationScope)
    }
} 