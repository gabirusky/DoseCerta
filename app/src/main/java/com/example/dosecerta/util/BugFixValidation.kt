package com.example.dosecerta.util

import android.util.Log
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.model.MedicationLog
import com.example.dosecerta.data.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

object BugFixValidation {
    
    private const val TAG = "BugFixValidation"
    
    /**
     * Validates that Bug #1 (duplicate logs) has been fixed
     * Returns true if no duplicates found, false otherwise
     */
    fun validateNoDuplicateLogs(
        repository: MedicationRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        onResult: (Boolean, String) -> Unit
    ) {
        scope.launch {
            try {
                Log.d(TAG, "Starting duplicate logs validation")
                
                // Get all logs from the database
                val allLogs = repository.getLogsBetweenDatesSync(
                    Date(0), 
                    Date(Long.MAX_VALUE)
                )
                
                // Group by medication ID and scheduled time
                val duplicateGroups = allLogs
                    .filter { it.scheduledTime != null }
                    .groupBy { log ->
                        val scheduledCal = Calendar.getInstance().apply { time = log.scheduledTime!! }
                        Triple(
                            log.medicationId,
                            scheduledCal.get(Calendar.HOUR_OF_DAY),
                            scheduledCal.get(Calendar.MINUTE)
                        )
                    }
                    .filter { (_, logs) -> logs.size > 1 }
                
                val duplicateCount = duplicateGroups.values.sumOf { it.size - 1 }
                
                if (duplicateCount == 0) {
                    val message = "✅ Bug #1 Fix Validated: No duplicate logs found"
                    Log.i(TAG, message)
                    onResult(true, message)
                } else {
                    val message = "❌ Bug #1 Still Present: Found $duplicateCount duplicate logs across ${duplicateGroups.size} medication schedules"
                    Log.w(TAG, message)
                    
                    // Log details of duplicates found
                    duplicateGroups.forEach { (key, logs) ->
                        Log.w(TAG, "Duplicate logs for medication ${key.first} at ${key.second}:${key.third}: ${logs.map { "${it.status}(${it.id})" }}")
                    }
                    
                    onResult(false, message)
                }
                
            } catch (e: Exception) {
                val message = "❌ Error validating duplicate logs: ${e.message}"
                Log.e(TAG, message, e)
                onResult(false, message)
            }
        }
    }
    
    /**
     * Validates that taken medications are properly prioritized over skipped ones
     */
    fun validateTakenPriorityOverSkipped(
        repository: MedicationRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        onResult: (Boolean, String) -> Unit
    ) {
        scope.launch {
            try {
                Log.d(TAG, "Starting taken priority validation")
                
                val allLogs = repository.getLogsBetweenDatesSync(
                    Date(0),
                    Date(Long.MAX_VALUE)
                )
                
                // Group by medication and scheduled time
                val logGroups = allLogs
                    .filter { it.scheduledTime != null }
                    .groupBy { log ->
                        val scheduledCal = Calendar.getInstance().apply { time = log.scheduledTime!! }
                        Triple(
                            log.medicationId,
                            scheduledCal.get(Calendar.HOUR_OF_DAY),
                            scheduledCal.get(Calendar.MINUTE)
                        )
                    }
                
                var incorrectPriorityCount = 0
                val problemCases = mutableListOf<String>()
                
                for ((key, logs) in logGroups) {
                    if (logs.size > 1) {
                        // Check if there are both TAKEN and SKIPPED logs
                        val hasTaken = logs.any { it.status == LogStatus.TAKEN }
                        val hasSkipped = logs.any { it.status == LogStatus.SKIPPED }
                        
                        if (hasTaken && hasSkipped) {
                            incorrectPriorityCount++
                            problemCases.add("Medication ${key.first} at ${key.second}:${key.third} has both TAKEN and SKIPPED logs")
                        }
                    }
                }
                
                if (incorrectPriorityCount == 0) {
                    val message = "✅ Taken Priority Validated: No conflicts between TAKEN and SKIPPED logs"
                    Log.i(TAG, message)
                    onResult(true, message)
                } else {
                    val message = "❌ Taken Priority Issue: Found $incorrectPriorityCount cases with both TAKEN and SKIPPED logs"
                    Log.w(TAG, message)
                    problemCases.forEach { Log.w(TAG, it) }
                    onResult(false, message)
                }
                
            } catch (e: Exception) {
                val message = "❌ Error validating taken priority: ${e.message}"
                Log.e(TAG, message, e)
                onResult(false, message)
            }
        }
    }
    
    /**
     * Simulates the notification reliability by checking alarm scheduling capabilities
     */
    fun validateNotificationReliability(
        context: android.content.Context,
        onResult: (Boolean, String) -> Unit
    ) {
        try {
            Log.d(TAG, "Starting notification reliability validation")
            
            val permissionStatus = PermissionHelper.checkAllPermissions(context)
            val issues = mutableListOf<String>()
            
            if (!permissionStatus.hasNotificationPermission) {
                issues.add("Missing notification permission")
            }
            
            if (!permissionStatus.canScheduleExactAlarms) {
                issues.add("Cannot schedule exact alarms")
            }
            
            if (permissionStatus.isBatteryOptimized) {
                issues.add("App is battery optimized (may affect background notifications)")
            }
            
            // Check if notification helper is working
            val notificationsEnabled = NotificationHelper.areNotificationsEnabled(context)
            if (!notificationsEnabled) {
                issues.add("Notifications are disabled at system level")
            }
            
            if (issues.isEmpty()) {
                val message = "✅ Bug #2 Fix Validated: All notification requirements met"
                Log.i(TAG, message)
                onResult(true, message)
            } else {
                val message = "⚠️ Bug #2 Potential Issues: ${issues.joinToString(", ")}"
                Log.w(TAG, message)
                onResult(false, message)
            }
            
        } catch (e: Exception) {
            val message = "❌ Error validating notification reliability: ${e.message}"
            Log.e(TAG, message, e)
            onResult(false, message)
        }
    }
    
    /**
     * Comprehensive validation of both bug fixes
     */
    fun validateAllBugFixes(
        context: android.content.Context,
        repository: MedicationRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        onResult: (ValidationResult) -> Unit
    ) {
        scope.launch {
            val results = mutableListOf<String>()
            var allPassed = true
            
            // Validate Bug #1: Duplicate logs
            validateNoDuplicateLogs(repository, scope) { passed, message ->
                results.add("Bug #1 (Duplicates): $message")
                if (!passed) allPassed = false
            }
            
            // Validate taken priority
            validateTakenPriorityOverSkipped(repository, scope) { passed, message ->
                results.add("Priority Logic: $message")
                if (!passed) allPassed = false
            }
            
            // Validate Bug #2: Notification reliability
            validateNotificationReliability(context) { passed, message ->
                results.add("Bug #2 (Notifications): $message")
                if (!passed) allPassed = false
            }
            
            val finalResult = ValidationResult(
                allTestsPassed = allPassed,
                results = results,
                summary = if (allPassed) "🎉 All bug fixes validated successfully!" else "⚠️ Some issues remain or new issues detected"
            )
            
            Log.i(TAG, "Validation complete: ${finalResult.summary}")
            finalResult.results.forEach { Log.i(TAG, "  $it") }
            
            onResult(finalResult)
        }
    }
    
    /**
     * Data class to hold validation results
     */
    data class ValidationResult(
        val allTestsPassed: Boolean,
        val results: List<String>,
        val summary: String
    )
    
    /**
     * Quick check to verify database integrity
     */
    fun checkDatabaseIntegrity(
        repository: MedicationRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        onResult: (Boolean, String) -> Unit
    ) {
        scope.launch {
            try {
                val allLogs = repository.getLogsBetweenDatesSync(Date(0), Date(Long.MAX_VALUE))
                val logsWithoutScheduledTime = allLogs.count { it.scheduledTime == null }
                val logsWithFutureScheduledTime = allLogs.count { 
                    it.scheduledTime != null && it.scheduledTime!!.after(Date()) 
                }
                
                val issues = mutableListOf<String>()
                if (logsWithoutScheduledTime > 0) {
                    issues.add("$logsWithoutScheduledTime logs missing scheduled time")
                }
                
                if (issues.isEmpty()) {
                    val message = "✅ Database integrity check passed (${allLogs.size} logs checked, $logsWithFutureScheduledTime future scheduled)"
                    onResult(true, message)
                } else {
                    val message = "⚠️ Database integrity issues: ${issues.joinToString(", ")}"
                    onResult(false, message)
                }
                
            } catch (e: Exception) {
                val message = "❌ Database integrity check failed: ${e.message}"
                onResult(false, message)
            }
        }
    }
}