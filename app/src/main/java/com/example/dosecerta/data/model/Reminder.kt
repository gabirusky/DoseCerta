package com.example.dosecerta.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE // If medication is deleted, delete associated reminders
        )
    ],
    indices = [Index(value = ["medicationId"])] // Index for faster queries on medicationId
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val medicationId: Int,
    val hour: Int, // 0-23
    val minute: Int, // 0-59
    val isEnabled: Boolean = true // To easily enable/disable specific reminders
) 