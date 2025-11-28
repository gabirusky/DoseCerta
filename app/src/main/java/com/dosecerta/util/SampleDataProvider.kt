package com.dosecerta.util

import android.content.Context
import com.dosecerta.data.local.DoseCertaDatabase
import com.dosecerta.data.local.entity.Medication
import com.dosecerta.data.local.entity.Schedule
import com.dosecerta.data.model.Frequency
import com.dosecerta.data.model.PharmaceuticalForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Utility to provide sample data for testing the app.
 */
object SampleDataProvider {
    
    /**
     * Insert sample medications and schedules into the database.
     * Call this on first app launch or for testing purposes.
     */
    suspend fun insertSampleData(context: Context) = withContext(Dispatchers.IO) {
        val database = DoseCertaDatabase.getDatabase(context)
        
        // Check if data already exists
        val existingCount = database.medicationDao().getActiveMedicationCount().firstOrNull() ?: 0
        if (existingCount > 0) {
            // Sample data already inserted
            return@withContext
        }
        
        // Sample Medication 1: Omeprazol
        val omeprazolId = database.medicationDao().insert(
            Medication(
                name = "Omeprazol",
                dosage = "20",
                unit = "mg",
                pharmaceuticalForm = PharmaceuticalForm.CAPSULE,
                frequency = Frequency.DAILY,
                notes = "Tomar em jejum, 30 minutos antes do café da manhã",
                isActive = true
            )
        )
        
        // Schedule for Omeprazol: 08:00 daily
        database.scheduleDao().insert(
            Schedule(
                medicationId = omeprazolId,
                timeInMinutes = 8 * 60, // 08:00
                daysOfWeek = emptyList(), // Daily
                isActive = true
            )
        )
        
        // Sample Medication 2: Desvenlafaxina
        val desvenlafaxinaId = database.medicationDao().insert(
            Medication(
                name = "Desvenlafaxina",
                dosage = "100",
                unit = "mg",
                pharmaceuticalForm = PharmaceuticalForm.TABLET,
                frequency = Frequency.DAILY,
                notes = "Tomar à noite, antes de dormir",
                isActive = true
            )
        )
        
        // Schedule for Desvenlafaxina: 20:00 daily
        database.scheduleDao().insert(
            Schedule(
                medicationId = desvenlafaxinaId,
                timeInMinutes = 20 * 60, // 20:00
                daysOfWeek = emptyList(), // Daily
                isActive = true
            )
        )
        
        // Sample Medication 3: Vitamina D
        val vitaminaDId = database.medicationDao().insert(
            Medication(
                name = "Vitamina D",
                dosage = "2000",
                unit = "UI",
                pharmaceuticalForm = PharmaceuticalForm.DROPS,
                frequency = Frequency.DAILY,
                notes = "5 gotas ao dia, preferencialmente após o almoço",
                isActive = true
            )
        )
        
        // Schedule for Vitamina D: 12:00 daily
        database.scheduleDao().insert(
            Schedule(
                medicationId = vitaminaDId,
                timeInMinutes = 12 * 60, // 12:00
                daysOfWeek = emptyList(), // Daily
                isActive = true
            )
        )
    }
}
