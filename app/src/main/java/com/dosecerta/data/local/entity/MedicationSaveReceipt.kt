package com.dosecerta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Opaque request receipt prevents a restored form from repeating an already committed save. */
@Entity(tableName = "medication_save_receipts")
data class MedicationSaveReceipt(@PrimaryKey val requestId: String, val medicationId: Long)
