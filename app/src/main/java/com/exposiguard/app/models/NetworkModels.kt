package com.exposiguard.app.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "wifi_networks")
data class WiFiNetwork(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ssid: String,
    val bssid: String,
    val signalStrength: Int, // dBm
    val frequency: Double, // GHz
    val security: String,
    val channel: Int,
    val capabilities: String,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    val exposureLevel: Double
        get() = calculateWiFiExposure(signalStrength, frequency)

    val signalQuality: SignalQuality
        get() = when {
            signalStrength >= -50 -> SignalQuality.EXCELLENT
            signalStrength >= -60 -> SignalQuality.GOOD
            signalStrength >= -70 -> SignalQuality.FAIR
            else -> SignalQuality.POOR
        }

    private fun calculateWiFiExposure(signalStrength: Int, frequency: Double): Double {
        // Simplified exposure calculation based on signal strength and frequency
        val baseExposure = when {
            frequency < 3.0 -> 0.001 // 2.4GHz
            else -> 0.0008 // 5GHz
        }

        // Signal strength factor (stronger signal = higher exposure)
        val signalFactor = (100 + signalStrength) / 100.0

        return baseExposure * signalFactor
    }
}

@Parcelize
@Entity(tableName = "bluetooth_devices")
data class BluetoothDevice(
    @PrimaryKey
    val address: String,
    val name: String,
    val type: BluetoothDeviceType = BluetoothDeviceType.UNKNOWN,
    val signalStrength: Int, // dBm
    val isConnected: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    val exposureLevel: Double
        get() = calculateBluetoothExposure(signalStrength)

    private fun calculateBluetoothExposure(signalStrength: Int): Double {
        // Bluetooth exposure is generally lower than WiFi
        val baseExposure = 0.0005
        val signalFactor = (100 + signalStrength) / 100.0
        return baseExposure * signalFactor
    }
}

enum class SignalQuality {
    EXCELLENT, GOOD, FAIR, POOR;

    val displayName: String
        get() = when (this) {
            EXCELLENT -> "Excelente"
            GOOD -> "Buena"
            FAIR -> "Regular"
            POOR -> "Mala"
        }
}

enum class BluetoothDeviceType {
    HEADPHONES, SPEAKER, WATCH, PHONE, COMPUTER, UNKNOWN;

    val displayName: String
        get() = when (this) {
            HEADPHONES -> "Audífonos"
            SPEAKER -> "Altavoz"
            WATCH -> "Reloj"
            PHONE -> "Teléfono"
            COMPUTER -> "Computadora"
            UNKNOWN -> "Desconocido"
        }
}
