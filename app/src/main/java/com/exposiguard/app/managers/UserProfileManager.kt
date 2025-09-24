package com.exposiguard.app.managers

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject

/**
 * UserProfileManager - Gestor para almacenar y recuperar el perfil de usuario (peso y altura)
 * Usa SharedPreferences para persistencia local
 */
class UserProfileManager @Inject constructor(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("exposiguard_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WEIGHT = "user_weight"
        private const val KEY_HEIGHT = "user_height"
        private const val KEY_EXPOSURE_STANDARD = "exposure_standard"
        private const val DEFAULT_WEIGHT = 70.0
        private const val DEFAULT_HEIGHT = 170.0
        private const val DEFAULT_STANDARD = "FCC"
    }

    /**
     * Guarda el peso del usuario
     */
    fun saveWeight(weight: Double) {
        sharedPreferences.edit().putFloat(KEY_WEIGHT, weight.toFloat()).apply()
    }

    /**
     * Guarda la altura del usuario
     */
    fun saveHeight(height: Double) {
        sharedPreferences.edit().putFloat(KEY_HEIGHT, height.toFloat()).apply()
    }

    /**
     * Guarda tanto peso como altura
     */
    fun saveUserProfile(weight: Double, height: Double) {
        sharedPreferences.edit()
            .putFloat(KEY_WEIGHT, weight.toFloat())
            .putFloat(KEY_HEIGHT, height.toFloat())
            .apply()
    }

    /**
     * Obtiene el peso del usuario (devuelve valor por defecto si no está guardado)
     */
    fun getWeight(): Double {
        return sharedPreferences.getFloat(KEY_WEIGHT, DEFAULT_WEIGHT.toFloat()).toDouble()
    }

    /**
     * Obtiene la altura del usuario (devuelve valor por defecto si no está guardado)
     */
    fun getHeight(): Double {
        return sharedPreferences.getFloat(KEY_HEIGHT, DEFAULT_HEIGHT.toFloat()).toDouble()
    }

    /**
     * Obtiene el perfil completo del usuario
     */
    fun getUserProfile(): EMFManager.UserProfile {
        return EMFManager.UserProfile(
            weight = getWeight(),
            height = getHeight()
        )
    }

    /**
     * Verifica si el usuario ya ha configurado su perfil
     */
    fun isProfileConfigured(): Boolean {
        return sharedPreferences.contains(KEY_WEIGHT) &&
               sharedPreferences.contains(KEY_HEIGHT) &&
               sharedPreferences.contains(KEY_EXPOSURE_STANDARD)
    }

    /**
     * Guarda el estándar de exposición seleccionado
     */
    fun saveExposureStandard(standard: String) {
        sharedPreferences.edit().putString(KEY_EXPOSURE_STANDARD, standard).apply()
    }

    /**
     * Obtiene el estándar de exposición seleccionado
     */
    fun getExposureStandard(): String {
        return sharedPreferences.getString(KEY_EXPOSURE_STANDARD, DEFAULT_STANDARD) ?: DEFAULT_STANDARD
    }

    /**
     * Obtiene el peso en kilogramos
     */
    fun getWeightInCurrentUnits(): Double {
        return getWeight()
    }

    /**
     * Obtiene la altura en centímetros
     */
    fun getHeightInCurrentUnits(): Double {
        return getHeight()
    }

    /**
     * Guarda el peso en kilogramos
     */
    fun saveWeightInCurrentUnits(weight: Double) {
        saveWeight(weight)
    }

    /**
     * Guarda la altura en centímetros
     */
    fun saveHeightInCurrentUnits(height: Double) {
        saveHeight(height)
    }
}
