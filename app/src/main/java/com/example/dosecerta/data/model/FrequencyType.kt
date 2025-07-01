package com.example.dosecerta.data.model

enum class FrequencyType {
    DAILY, // Every day
    EVERY_X_DAYS, // e.g., Every 3 days
    SPECIFIC_DAYS_OF_WEEK, // e.g., Monday, Wednesday, Friday
    AS_NEEDED // No regular schedule, reminders might be manual or off
} 