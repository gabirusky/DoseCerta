package com.example.dosecerta.util

import android.util.Log
import com.example.dosecerta.data.model.LogStatus
import com.example.dosecerta.data.repository.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

object DatabaseCleanupUtil {
    
    private const val TAG = "DatabaseCleanupUtil"
    
    /**
     * Cleans up duplicate medication logs in the database.
     * Keeps the most relevant log (TAKEN > SKIPPED > MISSED) for each medication and scheduled time.
     */
    fun cleanupDuplicateLogs(
        repository: MedicationRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        scope.launch {
            try {
                Log.d(TAG, "Starting database cleanup for duplicate logs")
                
                // Get all logs and group by medication ID and scheduled time
                val allLogs = repository.getLogsBetweenDatesSync(
                    Date(0), // From beginning of time
                    Date(Long.MAX_VALUE) // To end of time
                )
                
                val duplicateGroups = allLogs
                    .filter { it.scheduledTime != null }
                    .groupBy { log ->
                        val scheduledCal = Calendar.getInstance().apply { time = log.scheduledTime!! }
                        Triple(
                            log.medicationId,
                            scheduledCal.get(Calendar.YEAR),
                            scheduledCal.get(Calendar.DAY_OF_YEAR)
                        )
                    }
                    .filter { (_, logs) -> logs.size > 1 }
                
                var totalDuplicatesRemoved = 0
                
                for ((key, duplicateLogs) in duplicateGroups) {
                    // Further group by exact scheduled time (hour and minute)
                    val exactTimeGroups = duplicateLogs.groupBy { log ->
                        val scheduledCal = Calendar.getInstance().apply { time = log.scheduledTime!! }
                        Pair(
                            scheduledCal.get(Calendar.HOUR_OF_DAY),
                            scheduledCal.get(Calendar.MINUTE)
                        )
                    }
                    
                    for ((timeKey, logsAtSameTime) in exactTimeGroups) {
                        if (logsAtSameTime.size > 1) {
                            // Sort by priority: TAKEN > SKIPPED > MISSED, then by most recent timestamp
                            val sortedLogs = logsAtSameTime.sortedWith(
                                compareByDescending<com.example.dosecerta.data.model.MedicationLog> { log ->
                                    when (log.status) {
                                        LogStatus.TAKEN -> 3
                                        LogStatus.SKIPPED -> 2
                                        LogStatus.MISSED -> 1
                                    }
                                }.thenByDescending { it.logTimestamp }
                            )
                            
                            // Keep the first (highest priority) log, delete the rest
                            val keepLog = sortedLogs.first()
                            val logsToDelete = sortedLogs.drop(1)
                            
                            for (logToDelete in logsToDelete) {
                                repository.deleteLogById(logToDelete.id)
                                totalDuplicatesRemoved++
                            }
                            
                            Log.d(TAG, "Cleaned up ${logsToDelete.size} duplicate logs for medication ${keepLog.medicationId} at ${timeKey.first}:${timeKey.second}, kept ${keepLog.status}")
                        }
                    }
                }
                
                Log.i(TAG, "Database cleanup completed. Removed $totalDuplicatesRemoved duplicate logs")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during database cleanup", e)
            }
        }
    }
    
    /**
     * Validates and fixes medication logs with missing scheduled times
     */
    fun fixMissingScheduledTimes(
        repository: MedicationRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        scope.launch {
            try {
                Log.d(TAG, "Starting fix for logs with missing scheduled times")
                
                val allLogs = repository.getLogsBetweenDatesSync(
                    Date(0),
                    Date(Long.MAX_VALUE)
                )
                
                val logsWithMissingScheduledTime = allLogs.filter { it.scheduledTime == null }
                
                for (log in logsWithMissingScheduledTime) {
                    // For logs without scheduled time, set it to the log timestamp
                    val updatedLog = log.copy(scheduledTime = log.logTimestamp)
                    repository.updateLog(updatedLog)
                }
                
                Log.i(TAG, "Fixed ${logsWithMissingScheduledTime.size} logs with missing scheduled times")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fixing missing scheduled times", e)
            }
        }
    }
    
    /**
     * Performs a comprehensive database cleanup
     */
    fun performFullCleanup(
        repository: MedicationRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    ) {
        scope.launch {
            Log.i(TAG, "Starting comprehensive database cleanup")
            
            // Step 1: Fix missing scheduled times
            fixMissingScheduledTimes(repository, scope)
            
            // Step 2: Clean up duplicates
            cleanupDuplicateLogs(repository, scope)
            
            Log.i(TAG, "Comprehensive database cleanup completed")
        }
    }
}