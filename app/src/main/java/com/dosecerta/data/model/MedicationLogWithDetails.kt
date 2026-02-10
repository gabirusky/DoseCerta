package com.dosecerta.data.model

import com.dosecerta.data.local.entity.MedicationLog

/**
 * Data class that combines MedicationLog with medication details.
 * Used for displaying logs with medication names in the UI.
 */
data class MedicationLogWithDetails(
    val log: MedicationLog,
    val medicationName: String,
    val dosage: String,
    val unit: String
)
