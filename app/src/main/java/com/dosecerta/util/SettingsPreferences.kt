package com.dosecerta.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-based preferences management for app settings.
 */
class SettingsPreferences(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        
        const val LANGUAGE_PORTUGUESE = "pt"
        const val LANGUAGE_ENGLISH = "en"
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
}
