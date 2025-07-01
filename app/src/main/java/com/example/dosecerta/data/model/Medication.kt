package com.example.dosecerta.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date // Import Date

// Enum for Dosage Form
enum class DosageForm {
    TABLET, CAPSULE, LIQUID, INJECTION, CREAM, OINTMENT, DROPS, SPRAY, OTHER
}

// Enum for Strength Unit
enum class StrengthUnit {
    MG, MCG, G, ML, PERCENTAGE, IU, OTHER
}

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dosage: String, // e.g., "1", "2", "10ml"
    val dosageForm: DosageForm,
    val strength: String?, // e.g., "500", "10" - Optional
    val strengthUnit: StrengthUnit?, // e.g., MG, ML - Optional, linked to strength
    
    // New Frequency Fields
    val frequencyType: FrequencyType = FrequencyType.DAILY, // Default to Daily
    val frequencyIntervalDays: Int? = null, // e.g., 3 for EVERY_X_DAYS
    val frequencyDaysOfWeek: String? = null, // e.g., "1,3,5" for Mon, Wed, Fri (Calendar.DAY_OF_WEEK)

    val startDate: Date? = null, // Add start date for frequency anchoring

    val frequency: String? = null, // Old field - Keep temporarily for migration?
    val notes: String? = null // Optional field for additional notes
) 