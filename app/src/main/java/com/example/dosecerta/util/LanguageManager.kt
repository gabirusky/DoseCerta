package com.example.dosecerta.util

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.*

object LanguageManager {
    
    private const val PREFS_NAME = "dosecerta_language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val LANGUAGE_ENGLISH = "en"
    private const val LANGUAGE_PORTUGUESE = "pt"
    
    enum class Language(val code: String, val displayName: String) {
        ENGLISH("en", "English"),
        PORTUGUESE("pt", "Português")
    }
    
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun setLanguage(context: Context, language: Language) {
        getPreferences(context).edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
        applyLanguage(context, language.code)
    }
    
    fun getCurrentLanguage(context: Context): Language {
        val savedLanguage = getPreferences(context).getString(KEY_LANGUAGE, null)
        return when (savedLanguage) {
            LANGUAGE_PORTUGUESE -> Language.PORTUGUESE
            LANGUAGE_ENGLISH -> Language.ENGLISH
            else -> {
                // If no language is saved, detect system language
                val systemLanguage = getSystemLanguage()
                if (systemLanguage.startsWith("pt")) {
                    Language.PORTUGUESE
                } else {
                    Language.ENGLISH
                }
            }
        }
    }
    
    fun applyLanguage(context: Context, languageCode: String? = null) {
        val language = languageCode ?: getCurrentLanguage(context).code
        val locale = when (language) {
            LANGUAGE_PORTUGUESE -> Locale("pt", "BR")
            else -> Locale("en", "US")
        }
        
        Locale.setDefault(locale)
        
        val configuration = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
        }
        
        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
    
    fun initializeLanguage(context: Context) {
        val currentLanguage = getCurrentLanguage(context)
        applyLanguage(context, currentLanguage.code)
    }
    
    private fun getSystemLanguage(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Locale.getDefault().language
        } else {
            @Suppress("DEPRECATION")
            Locale.getDefault().language
        }
    }
    
    fun getAllLanguages(): List<Language> {
        return Language.values().toList()
    }
    
    fun getLanguageByCode(code: String): Language {
        return Language.values().find { it.code == code } ?: Language.ENGLISH
    }
}