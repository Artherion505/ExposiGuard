package com.exposiguard.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import javax.inject.Inject
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import java.util.Properties

@HiltAndroidApp
class ExposiGuardApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()

        try {
            // Configurar modo estricto de forma más permisiva para evitar crashes
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()  // Solo detectar operaciones de red
                    .detectDiskReads() // Detectar lecturas de disco
                    .detectDiskWrites() // Detectar escrituras de disco
                    .penaltyLog() // Solo log, no crash
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectActivityLeaks() // Detectar leaks de actividades
                    .detectLeakedClosableObjects() // Detectar objetos no cerrados
                    .penaltyLog() // Solo log, no crash
                    .build()
            )

            // Cargar configuración de privacidad
            loadPrivacySettings()
        } catch (e: Exception) {
            // getString(R.string.comment_log_error_no_crash)
            android.util.Log.e("ExposiGuardApplication", "Error in onCreate", e)
        }
    }

    private fun loadPrivacySettings() {
        try {
            val properties = Properties()
            assets.open("privacy.properties").use { inputStream ->
                properties.load(inputStream)
            }

            // Aplicar configuraciones de privacidad
            val privacyEnabled = properties.getProperty("privacy.enabled", "false").toBoolean()
            val analyticsEnabled = properties.getProperty("analytics.enabled", "false").toBoolean()

            if (privacyEnabled) {
                // Configuraciones adicionales de privacidad
                try {
                    System.setProperty("androidx.lifecycle.ProcessLifecycleOwnerInitializer", "false")
                    System.setProperty("android.enableCrashReporting", "false")
                } catch (e: Exception) {
                    android.util.Log.w("ExposiGuardApplication", "Could not set privacy properties", e)
                }
            }

            android.util.Log.d("ExposiGuardApplication", "Privacy settings loaded: privacy=$privacyEnabled, analytics=$analyticsEnabled")

        } catch (e: Exception) {
            // Si no se puede cargar el archivo, continuar normalmente
            android.util.Log.w("ExposiGuardApplication", "Could not load privacy.properties", e)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
