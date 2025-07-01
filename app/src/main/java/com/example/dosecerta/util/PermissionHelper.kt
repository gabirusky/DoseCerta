package com.example.dosecerta.util

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {
    
    private const val TAG = "PermissionHelper"
    
    /**
     * Checks if the app has permission to schedule exact alarms
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true // No permission needed on older versions
        }
    }
    
    /**
     * Checks if the app has notification permission
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No explicit permission needed on older versions
        }
    }
    
    /**
     * Gets an intent to open exact alarm permission settings
     */
    fun getExactAlarmPermissionIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }
    
    /**
     * Gets an intent to open app notification settings
     */
    fun getNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    
    /**
     * Checks if battery optimization is disabled for the app
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // No battery optimization on older versions
        }
    }
    
    /**
     * Gets an intent to request battery optimization exemption
     */
    fun getBatteryOptimizationIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }
    
    /**
     * Comprehensive permission check for reliable notifications
     */
    fun checkAllPermissions(context: Context): PermissionStatus {
        val hasNotifications = hasNotificationPermission(context)
        val canScheduleExact = canScheduleExactAlarms(context)
        val batteryOptimized = !isBatteryOptimizationDisabled(context)
        
        Log.d(TAG, "Permission status: notifications=$hasNotifications, exactAlarms=$canScheduleExact, batteryOptimized=$batteryOptimized")
        
        return PermissionStatus(
            hasNotificationPermission = hasNotifications,
            canScheduleExactAlarms = canScheduleExact,
            isBatteryOptimized = batteryOptimized
        )
    }
    
    /**
     * Data class to hold permission status
     */
    data class PermissionStatus(
        val hasNotificationPermission: Boolean,
        val canScheduleExactAlarms: Boolean,
        val isBatteryOptimized: Boolean
    ) {
        fun isFullyGranted(): Boolean = hasNotificationPermission && canScheduleExactAlarms && !isBatteryOptimized
        
        fun getMissingPermissions(): List<String> {
            val missing = mutableListOf<String>()
            if (!hasNotificationPermission) missing.add("Notification Permission")
            if (!canScheduleExactAlarms) missing.add("Exact Alarm Permission")
            if (isBatteryOptimized) missing.add("Battery Optimization Exemption")
            return missing
        }
    }
}