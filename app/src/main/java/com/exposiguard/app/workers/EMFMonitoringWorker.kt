package com.exposiguard.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.exposiguard.app.managers.WiFiManager
import com.exposiguard.app.managers.SARManager
import com.exposiguard.app.managers.BluetoothManager
import com.exposiguard.app.managers.AmbientExposureManager
import com.exposiguard.app.managers.PhysicalSensorManager
import com.exposiguard.app.managers.HealthManager
import com.exposiguard.app.managers.EMFManager
import com.exposiguard.app.managers.UserProfileManager
import com.exposiguard.app.managers.PhoneUseManager
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class EMFMonitoringWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val userProfileManager: UserProfileManager,
    private val appUsageManager: com.exposiguard.app.managers.AppUsageManager,
    private val bluetoothManager: BluetoothManager,
    private val ambientManager: AmbientExposureManager,
    private val physicalSensorManager: PhysicalSensorManager,
    private val healthManager: HealthManager,
    private val emfManager: EMFManager,
    private val phoneUseManager: PhoneUseManager,
    private val wifiManager: WiFiManager,
    private val exposureRepository: ExposureRepository,
    private val sarManager: SARManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("exposiguard_settings", Context.MODE_PRIVATE)
            val monitorHome = prefs.getBoolean("monitor_home", true)
            val monitorWifi = prefs.getBoolean("monitor_wifi", true)
            val monitorEmf = prefs.getBoolean("monitor_emf", true)

            // Recolección básica: WiFi y SAR si están habilitados
            if (monitorWifi) {
                runCatching {
                    val wifiReadings = wifiManager.getExposureReadings()
                    if (wifiReadings.isNotEmpty()) exposureRepository.addExposureReadings(wifiReadings)
                }.onFailure { e -> android.util.Log.w("EMFMonitoringWorker", "WiFi sampling failed: ${e.message}") }
            }

            if (monitorEmf || monitorHome) {
                runCatching {
                    val sar = sarManager.getCurrentSARLevel()
                    val reading = ExposureReading(
                        timestamp = System.currentTimeMillis(),
                        wifiLevel = 0.0,
                        sarLevel = sar,
                        bluetoothLevel = 0.0,
                        type = ExposureType.SAR,
                        source = "Worker SAR"
                    )
                    exposureRepository.addExposureReading(reading)
                }.onFailure { e -> android.util.Log.w("EMFMonitoringWorker", "SAR sampling failed: ${e.message}") }
            }

            // Notificar a la app para refrescar UI si está en foreground
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.DataChanged)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("EMFMonitoringWorker", "EMF monitoring failed", e)
            Result.failure()
        }
    }
}
