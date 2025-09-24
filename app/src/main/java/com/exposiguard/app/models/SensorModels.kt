package com.exposiguard.app.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "health_data")
data class HealthData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val heartRate: Int, // BPM
    val stepsToday: Int,
    val caloriesBurned: Double,
    val activeMinutes: Int,
    val sleepHours: Double,
    val sleepQuality: Double, // 0-100
    val sleepEfficiency: Double, // 0-100
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    val healthScore: Double
        get() = calculateHealthScore()

    private fun calculateHealthScore(): Double {
        val heartRateScore = when {
            heartRate in 60..100 -> 100.0
            heartRate in 50..120 -> 80.0
            else -> 60.0
        }

        val sleepScore = sleepQuality
        val activityScore = (activeMinutes / 30.0).coerceAtMost(100.0)

        return (heartRateScore + sleepScore + activityScore) / 3.0
    }
}

@Parcelize
@Entity(tableName = "noise_data")
data class NoiseData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val decibels: Double,
    val frequencySpectrum: List<Double>,
    val dominantFrequency: Double,
    val noiseHistory: List<Double>,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable {

    val noiseLevel: NoiseLevel
        get() = when {
            decibels < 30 -> NoiseLevel.VERY_QUIET
            decibels < 50 -> NoiseLevel.QUIET
            decibels < 70 -> NoiseLevel.MODERATE
            decibels < 90 -> NoiseLevel.LOUD
            else -> NoiseLevel.VERY_LOUD
        }

    val emfInterference: Double
        get() = calculateEMFInterference()

    private fun calculateEMFInterference(): Double {
        // Simplified EMF interference calculation based on noise levels
        return when (noiseLevel) {
            NoiseLevel.VERY_QUIET -> 0.1
            NoiseLevel.QUIET -> 0.3
            NoiseLevel.MODERATE -> 0.6
            NoiseLevel.LOUD -> 0.8
            NoiseLevel.VERY_LOUD -> 1.0
        }
    }
}

enum class NoiseLevel {
    VERY_QUIET, QUIET, MODERATE, LOUD, VERY_LOUD;

    val displayName: String
        get() = when (this) {
            VERY_QUIET -> "Muy silencioso"
            QUIET -> "Silencioso"
            MODERATE -> "Moderado"
            LOUD -> "Ruidoso"
            VERY_LOUD -> "Muy ruidoso"
        }

    val maxDecibels: Int
        get() = when (this) {
            VERY_QUIET -> 30
            QUIET -> 50
            MODERATE -> 70
            LOUD -> 90
            VERY_LOUD -> Int.MAX_VALUE
        }
}
