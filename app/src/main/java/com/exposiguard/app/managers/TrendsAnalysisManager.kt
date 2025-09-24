package com.exposiguard.app.managers

import com.exposiguard.app.data.CombinedExposureReading
import com.exposiguard.app.data.ExposureReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendsAnalysisManager @Inject constructor() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var analysisJob: Job? = null

    var isAnalyzing = false
        private set

    // Reference managers for data collection
    var wifiManager: WiFiManager? = null
    var noiseManager: NoiseManager? = null
    var healthManager: HealthManager? = null

    fun startAnalysis() {
        isAnalyzing = true
        analysisJob?.cancel()

        analysisJob = scope.launch {
            while (isActive) {
                // Analysis logic can be added here
                delay(60000) // Analyze every minute
            }
        }
    }

    fun stopAnalysis() {
        isAnalyzing = false
        analysisJob?.cancel()
    }

    fun analyzeTrends(readings: List<ExposureReading>): String {
        if (readings.isEmpty()) {
            return "No exposure data available"
        }

        val avgWifi = readings.map { it.wifiLevel }.average()
        val avgSar = readings.map { it.sarLevel }.average()
        val avgBluetooth = readings.map { it.bluetoothLevel }.average()
        val avgTotal = readings.map { it.wifiLevel + it.sarLevel + it.bluetoothLevel }.average()

        // Análisis de redes de compañías telefónicas
        val carrierReadings = readings.filter { reading ->
            reading.source.contains("Movistar") ||
            reading.source.contains("Vodafone") ||
            reading.source.contains("Orange") ||
            reading.source.contains("Telekom") ||
            reading.source.contains("T-Mobile") ||
            reading.source.contains("AT&T") ||
            reading.source.contains("Verizon")
        }

        val carrierExposure = if (carrierReadings.isNotEmpty()) {
            carrierReadings.map { it.wifiLevel + it.sarLevel }.average()
        } else 0.0

        // Análisis de dispositivos Bluetooth
        val bluetoothReadings = readings.filter { it.bluetoothLevel > 0 }
        val bluetoothSources = bluetoothReadings.map { it.source }.distinct()

        return """
        📊 Exposure Analysis:
    Average WiFi Level: ${String.format(java.util.Locale.getDefault(), "%.3f", avgWifi)}
    Average SAR Level: ${String.format(java.util.Locale.getDefault(), "%.3f", avgSar)} W/kg
    Average Bluetooth Level: ${String.format(java.util.Locale.getDefault(), "%.3f", avgBluetooth)} W/kg
    Average Total Exposure: ${String.format(java.util.Locale.getDefault(), "%.3f", avgTotal)} W/kg

        🚨 Carrier Networks: ${carrierReadings.size} detections
    ${if (carrierExposure > 0) "Average Carrier Exposure: ${String.format(java.util.Locale.getDefault(), "%.3f", carrierExposure)} W/kg" else ""}

        🎧 Bluetooth Devices: ${bluetoothSources.size} detected
        ${if (bluetoothSources.isNotEmpty()) "Devices: ${bluetoothSources.joinToString(", ")}" else ""}

        Total Readings: ${readings.size}
        """.trimIndent()
    }

    fun getCombinedExposureData(readings: List<ExposureReading>): List<CombinedExposureReading> {
        return readings.map { reading ->
            CombinedExposureReading(
                timestamp = reading.timestamp,
                totalExposure = reading.wifiLevel + reading.sarLevel + reading.bluetoothLevel,
                wifiLevel = reading.wifiLevel,
                sarLevel = reading.sarLevel,
                bluetoothLevel = reading.bluetoothLevel,
                source = reading.source
            )
        }.sortedBy { it.timestamp }
    }

    fun getExposureTrendData(readings: List<ExposureReading>, timeWindowMinutes: Int = 60): List<CombinedExposureReading> {
        val currentTime = System.currentTimeMillis()
        val windowStart = currentTime - (timeWindowMinutes * 60 * 1000)

        return readings
            .filter { it.timestamp >= windowStart }
            .map { reading ->
                CombinedExposureReading(
                    timestamp = reading.timestamp,
                    totalExposure = reading.wifiLevel + reading.sarLevel + reading.bluetoothLevel,
                    wifiLevel = reading.wifiLevel,
                    sarLevel = reading.sarLevel,
                    bluetoothLevel = reading.bluetoothLevel,
                    source = reading.source
                )
            }
            .sortedBy { it.timestamp }
    }
}
