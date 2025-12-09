package com.dosecerta.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-based preferences management for app settings.
 */
class SettingsPreferences(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val MISSED_REMINDER_HOURS_KEY = intPreferencesKey("missed_reminder_hours")
        private val SETUP_COMPLETED_KEY = booleanPreferencesKey("setup_completed")
        private val TERMS_ACCEPTED_KEY = booleanPreferencesKey("terms_accepted")
        
        const val LANGUAGE_PORTUGUESE = "pt"
        const val LANGUAGE_ENGLISH = "en"
        
        const val DEFAULT_MISSED_REMINDER_HOURS = 2
        const val MIN_MISSED_REMINDER_HOURS = 1
        const val MAX_MISSED_REMINDER_HOURS = 10
    }
    
    /**
     * Get selected language preference.
     */
    val selectedLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: LANGUAGE_PORTUGUESE // Default to Portuguese
    }
    
    /**
     * Save language preference.
     */
    suspend fun saveLanguage(language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }
    
    /**
     * Get missed reminder hours preference.
     */
    val missedReminderHours: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[MISSED_REMINDER_HOURS_KEY] ?: DEFAULT_MISSED_REMINDER_HOURS
    }
    
    /**
     * Get missed reminder hours synchronously (blocking).
     */
    suspend fun getMissedReminderHoursSync(): Int {
        return missedReminderHours.first()
    }
    
    /**
     * Save missed reminder hours preference.
     */
    suspend fun saveMissedReminderHours(hours: Int) {
        val safeHours = hours.coerceIn(MIN_MISSED_REMINDER_HOURS, MAX_MISSED_REMINDER_HOURS)
        context.dataStore.edit { preferences ->
            preferences[MISSED_REMINDER_HOURS_KEY] = safeHours
        }
    }
    
    /**
     * Check if first-time setup has been completed.
     */
    val isSetupCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SETUP_COMPLETED_KEY] ?: false
    }
    
    /**
     * Check if setup is completed synchronously.
     */
    suspend fun isSetupCompletedSync(): Boolean {
        return isSetupCompleted.first()
    }
    
    /**
     * Mark setup as completed.
     */
    suspend fun setSetupCompleted() {
        context.dataStore.edit { preferences ->
            preferences[SETUP_COMPLETED_KEY] = true
        }
    }
    
    /**
     * Check if terms and privacy policy have been accepted.
     */
    val hasAcceptedTerms: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TERMS_ACCEPTED_KEY] ?: false
    }
    
    /**
     * Mark terms as accepted.
     */
    suspend fun acceptTerms() {
        context.dataStore.edit { preferences ->
            preferences[TERMS_ACCEPTED_KEY] = true
        }
    }
    
    /**
     * Check if terms are accepted synchronously.
     */
    suspend fun hasAcceptedTermsSync(): Boolean {
        return hasAcceptedTerms.first()
    }
}

