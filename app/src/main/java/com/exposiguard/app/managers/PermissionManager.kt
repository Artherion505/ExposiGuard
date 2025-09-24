package com.exposiguard.app.managers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.exposiguard.app.R

class PermissionManager(private val context: Context) {

    companion object {
        const val PERMISSION_REQUEST_CODE = 1001

        // Lista de permisos requeridos con sus explicaciones
        val REQUIRED_PERMISSIONS = mapOf(
            Manifest.permission.ACCESS_FINE_LOCATION to R.string.permission_location_fine_explanation,
            Manifest.permission.ACCESS_COARSE_LOCATION to R.string.permission_location_coarse_explanation,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION to R.string.permission_location_background_explanation,
            Manifest.permission.ACCESS_WIFI_STATE to R.string.permission_wifi_state_explanation,
            Manifest.permission.CHANGE_WIFI_STATE to R.string.permission_wifi_change_explanation,
            Manifest.permission.ACCESS_NETWORK_STATE to R.string.permission_network_state_explanation,
            Manifest.permission.READ_PHONE_STATE to R.string.permission_phone_state_explanation,
            Manifest.permission.BLUETOOTH to R.string.permission_bluetooth_explanation,
            Manifest.permission.BLUETOOTH_ADMIN to R.string.permission_bluetooth_admin_explanation,
            Manifest.permission.BLUETOOTH_CONNECT to R.string.permission_bluetooth_connect_explanation,
            Manifest.permission.BLUETOOTH_SCAN to R.string.permission_bluetooth_scan_explanation
        )

        // Permisos opcionales (menos críticos)
        val OPTIONAL_PERMISSIONS = mapOf(
            Manifest.permission.RECORD_AUDIO to R.string.permission_audio_explanation,
            Manifest.permission.ACTIVITY_RECOGNITION to R.string.permission_activity_explanation,
            Manifest.permission.BODY_SENSORS to R.string.permission_sensors_explanation,
            Manifest.permission.READ_PHONE_NUMBERS to R.string.permission_phone_numbers_explanation
        )
    }

    // Permisos de salud (solo en Android 13+)
    private val HEALTH_PERMISSIONS: Map<String, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        mapOf(
            "android.permission.health.READ_HEART_RATE" to R.string.permission_health_heart_rate_explanation,
            "android.permission.health.READ_STEPS" to R.string.permission_health_steps_explanation,
            "android.permission.health.READ_SLEEP" to R.string.permission_health_sleep_explanation
        )
    } else {
        emptyMap()
    }

    /**
     * Verifica si todos los permisos requeridos están otorgados
     */
    fun hasAllRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.keys.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Obtiene la lista de permisos requeridos que no están otorgados
     */
    fun getMissingRequiredPermissions(): List<String> {
        return REQUIRED_PERMISSIONS.keys.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Obtiene la lista de permisos opcionales que no están otorgados
     */
    fun getMissingOptionalPermissions(): List<String> {
        return OPTIONAL_PERMISSIONS.keys.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Obtiene la lista de permisos de salud que no están otorgados
     */
    fun getMissingHealthPermissions(): List<String> {
        return HEALTH_PERMISSIONS.keys.filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Solicita permisos faltantes
     */
    fun requestMissingPermissions(activity: Activity) {
        val missingRequired = getMissingRequiredPermissions()
        val missingOptional = getMissingOptionalPermissions()
        val missingHealth = getMissingHealthPermissions()

        val allMissing = missingRequired + missingOptional + missingHealth

        if (allMissing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                allMissing.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    /**
     * Verifica si se debe mostrar explicación para un permiso
     */
    fun shouldShowPermissionRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }

    /**
     * Obtiene la explicación de un permiso
     */
    fun getPermissionExplanation(permission: String): Int? {
        return REQUIRED_PERMISSIONS[permission]
            ?: OPTIONAL_PERMISSIONS[permission]
            ?: HEALTH_PERMISSIONS[permission]
    }

    /**
     * Verifica si un permiso específico está otorgado
     */
    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Obtiene todos los permisos faltantes organizados por categoría
     */
    fun getAllMissingPermissions(): Map<String, List<String>> {
        return mapOf(
            "required" to getMissingRequiredPermissions(),
            "optional" to getMissingOptionalPermissions(),
            "health" to getMissingHealthPermissions()
        )
    }
}
