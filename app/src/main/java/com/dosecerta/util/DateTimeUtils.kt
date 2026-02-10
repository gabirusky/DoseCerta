package com.dosecerta.util

import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

object DateTimeUtils {
    
    /**
     * Get greeting based on current time of day in Portuguese.
     */
    fun getGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 0..11 -> "Bom dia!"
            in 12..17 -> "Boa tarde!"
            else -> "Boa noite!"
        }
    }
    
    /**
     * Get current weekday name using device locale.
     */
    fun getWeekday(): String {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("EEEE", Locale.getDefault())
        return sdf.format(calendar.time).replaceFirstChar { it.uppercase() }
    }
    
    /**
     * Format time for display (HH:mm).
     */
    fun formatTime(timeInMinutes: Int): String {
        val hours = timeInMinutes / 60
        val minutes = timeInMinutes % 60
        return String.format("%02d:%02d", hours, minutes)
    }
    
    /**
     * Format timestamp to time string.
     */
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Format timestamp to date string.
     */
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd MMM", Locale("pt", "BR"))
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Format timestamp to full date string.
     */
    fun formatFullDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR"))
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Format timestamp to date and time string.
     */
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale("pt", "BR"))
        return sdf.format(Date(timestamp))
    }
    
    /**
     * Get current time in minutes since midnight.
     */
    fun getCurrentTimeInMinutes(): Int {
        val calendar = Calendar.getInstance()
        val hours = calendar.get(Calendar.HOUR_OF_DAY)
        val minutes = calendar.get(Calendar.MINUTE)
        return hours * 60 + minutes
    }
    
    /**
     * Get start of today timestamp.
     */
    fun getStartOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    /**
     * Get end of today timestamp.
     */
    fun getEndOfToday(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
    
    /**
     * Get timestamp for a specific time today.
     */
    fun getTimestampForToday(timeInMinutes: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, timeInMinutes / 60)
        calendar.set(Calendar.MINUTE, timeInMinutes % 60)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    /**
     * Get start of week timestamp (Sunday).
     */
    fun getStartOfWeek(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    /**
     * Get time difference string (e.g., "Em 15 min" or "há 2 h").
     */
    fun getTimeUntilString(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = timestamp - now
        val diffMinutes = (diff / (1000 * 60)).toInt()
        
        return when {
            diffMinutes < 0 -> {
                val absDiff = -diffMinutes
                when {
                    absDiff < 60 -> "há $absDiff min"
                    absDiff < 1440 -> "há ${absDiff / 60} h"
                    else -> "há ${absDiff / 1440} d"
                }
            }
            diffMinutes < 60 -> "Em $diffMinutes min"
            diffMinutes < 1440 -> "Em ${diffMinutes / 60} h"
            else -> "Em ${diffMinutes / 1440} d"
        }
    }
    
    /**
     * Check if schedule should trigger today based on frequency.
     */
    fun shouldScheduleToday(daysOfWeek: List<Int>): Boolean {
        if (daysOfWeek.isEmpty()) return true // Daily
        
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return daysOfWeek.contains(today)
    }
}
