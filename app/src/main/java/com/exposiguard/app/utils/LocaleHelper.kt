package com.exposiguard.app.utils

import android.content.Context
import android.content.res.Configuration
import java.util.*

object LocaleHelper {

    private const val SELECTED_LANGUAGE = "selected_language"
    private const val PREFS_NAME = "locale_prefs"

    fun setLocale(context: Context, languageCode: String): Context {
        persistLanguage(context, languageCode)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun persistLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(SELECTED_LANGUAGE, languageCode).apply()
    }

    fun getPersistedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(SELECTED_LANGUAGE, "es") ?: "es"
    }

    fun getLanguageName(languageCode: String): String {
        return when (languageCode) {
            "es" -> "Español"
            "en" -> "English"
            else -> "Español"
        }
    }

    fun getLanguageCodeFromName(languageName: String): String {
        return when (languageName) {
            "Español", "Spanish" -> "es"
            "English", "Inglés" -> "en"
            else -> "es"
        }
    }

    fun applyLanguageChange(context: Context, languageCode: String) {
        persistLanguage(context, languageCode)
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        if (context is android.app.Activity) {
            // Crear nuevo contexto con la configuración de idioma y recrear
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.applyOverrideConfiguration(config)
            context.recreate()
        }
    }

    private fun updateFragmentTexts(fragment: androidx.fragment.app.Fragment) {
        // Este método puede ser extendido para actualizar textos específicos de cada fragment
        // Por ahora, confiamos en que los fragments se actualicen automáticamente con las nuevas strings
        try {
            // Forzar actualización de la vista del fragment si está disponible
            fragment.view?.invalidate()
            fragment.view?.requestLayout()
        } catch (e: Exception) {
            android.util.Log.w("LocaleHelper", "Error updating fragment texts", e)
        }
    }
}