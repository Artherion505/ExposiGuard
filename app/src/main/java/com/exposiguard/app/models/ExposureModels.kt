package com.exposiguard.app.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "sar_data")
data class SARData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sarValue: Double, // W/kg
    val standard: SARStandard = SARStandard.FCC,
    val bodyPart: BodyPart = BodyPart.HEAD,
    val deviceModel: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    val isSafe: Boolean
        get() = sarValue <= standard.limit

    val percentageOfLimit: Double
        get() = (sarValue / standard.limit) * 100.0

    val riskLevel: RiskLevel
        get() = when {
            sarValue <= standard.limit * 0.5 -> RiskLevel.LOW
            sarValue <= standard.limit -> RiskLevel.MODERATE
            else -> RiskLevel.HIGH
        }
}

enum class SARStandard(val limit: Double) {
    FCC(1.6), // W/kg
    ICNIRP(2.0), // W/kg
    EU(2.0); // W/kg

    val displayName: String
        get() = when (this) {
            FCC -> "FCC (EE.UU.)"
            ICNIRP -> "ICNIRP (Internacional)"
            EU -> "UE"
        }
}

enum class BodyPart {
    HEAD, BODY, LIMBS;

    val displayName: String
        get() = when (this) {
            HEAD -> "Cabeza"
            BODY -> "Cuerpo"
            LIMBS -> "Extremidades"
        }
}

enum class RiskLevel {
    LOW, MODERATE, HIGH;

    val displayName: String
        get() = when (this) {
            LOW -> "Bajo Riesgo"
            MODERATE -> "Riesgo Moderado"
            HIGH -> "Alto Riesgo"
        }

    val color: String
        get() = when (this) {
            LOW -> "#4CAF50" // Green
            MODERATE -> "#FF9800" // Orange
            HIGH -> "#F44336" // Red
        }
}

@Parcelize
@Entity(tableName = "exposure_stats")
data class ExposureStats(
    @PrimaryKey
    val id: String = "current",
    val totalExposure: Double = 0.0, // W/m²
    val wifiExposure: Double = 0.0,
    val bluetoothExposure: Double = 0.0,
    val averageExposure: Double = 0.0,
    val peakExposure: Double = 0.0,
    val wifiNetworksCount: Int = 0,
    val bluetoothDevicesCount: Int = 0,
    val monitoringTime: Long = 0, // minutes
    val lastUpdate: Long = System.currentTimeMillis()
) : Parcelable {

    val exposureRisk: RiskLevel
        get() = when {
            totalExposure <= 0.08 -> RiskLevel.LOW // ICNIRP 24h limit
            totalExposure <= 0.571 -> RiskLevel.MODERATE // FCC 30min limit
            else -> RiskLevel.HIGH
        }

    val recommendations: List<String>
        get() = generateRecommendations()

    private fun generateRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()

        when (exposureRisk) {
            RiskLevel.HIGH -> {
                recommendations.add("Reduzca el tiempo de exposición a redes WiFi")
                recommendations.add("Manténgase alejado de routers y dispositivos Bluetooth")
                recommendations.add("Use modo avión cuando no necesite conectividad")
            }
            RiskLevel.MODERATE -> {
                recommendations.add("Limite el uso de dispositivos inalámbricos")
                recommendations.add("Mantenga distancia de al menos 1 metro de routers")
            }
            RiskLevel.LOW -> {
                recommendations.add("Su nivel de exposición es seguro")
                recommendations.add("Continúe monitoreando regularmente")
            }
        }

        if (wifiNetworksCount > 5) {
            recommendations.add("Considere desconectar redes WiFi no utilizadas")
        }

        return recommendations
    }
}
