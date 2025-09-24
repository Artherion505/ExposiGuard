package com.exposiguard.app.data

import android.graphics.Color
import java.util.Date

data class ExposureReading(
    val timestamp: Long = System.currentTimeMillis(),
    val wifiLevel: Double,
    val sarLevel: Double,
    val bluetoothLevel: Double = 0.0,
    val type: ExposureType,
    val source: String = "" // Para identificar la fuente específica (ej: "MovistarWiFi", "AirPods")
)

data class CombinedExposureReading(
    val timestamp: Long,
    val totalExposure: Double, // wifiLevel + sarLevel + bluetoothLevel
    val wifiLevel: Double,
    val sarLevel: Double,
    val bluetoothLevel: Double = 0.0,
    val source: String = ""
)

data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val type: BluetoothDeviceType,
    val estimatedSAR: Double
)

enum class BluetoothDeviceType {
    HEADPHONES,
    HEADSET,
    HANDSFREE,
    HIFI_AUDIO,
    UNKNOWN
}

data class DailyExposure(
    val date: Date,
    val readings: List<ExposureReading>
)

enum class ExposureType {
    WIFI, SAR, NOISE, HEALTH
}

enum class ExposureLevel(val color: Int, val description: String) {
    LOW(Color.GREEN, "Bajo"),
    MEDIUM(Color.YELLOW, "Medio"),
    HIGH(Color.rgb(255, 152, 0), "Alto"),
    CRITICAL(Color.RED, "Crítico")
}
