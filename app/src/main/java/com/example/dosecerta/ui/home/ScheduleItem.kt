package com.example.dosecerta.ui.home

import com.example.dosecerta.data.model.LogStatus // Import LogStatus
import com.example.dosecerta.data.model.FrequencyType // Import FrequencyType

// Represents a single scheduled medication time for display
data class ScheduleItem(
    val reminderId: Int,
    val medicationId: Int,
    val medicationName: String,
    val dosage: String,
    val hour: Int,
    val minute: Int,
    val frequencyType: FrequencyType, // Add frequency type
    var status: LogStatus? = null // Track if logged today (Taken, Skipped, or null if pending)
) 