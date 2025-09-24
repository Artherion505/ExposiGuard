package com.exposiguard.app.managers

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class EMFManager @Inject constructor() {

    // Estándares de exposición (W/kg)
    private val FCC_LIMIT = 1.6  // FCC (USA)
    private val ICNIRP_LIMIT = 2.0  // ICNIRP (Europa)

    // Datos del perfil de usuario
    data class UserProfile(
        val weight: Double, // kg
        val height: Double, // cm
        val imc: Double = 0.0 // IMC calculado
    )

    /**
     * Calcula el factor de absorción basado en el perfil del usuario
     * Usa IMC y características físicas para determinar capacidad de absorción
     */
    fun calculateAbsorptionFactor(userProfile: UserProfile): Double {
        val heightM = userProfile.height / 100.0
        val imc = userProfile.imc

        // Fórmula de Du Bois para superficie corporal (m²)
        val bsa = 0.007184 * userProfile.weight.pow(0.425) * heightM.pow(0.725)

        // Factor base de absorción
        var absorptionFactor = 0.015 // Factor base para exposición corporal completa

        // Ajustar por IMC (índice de masa corporal)
        val imcAdjustment = when {
            imc < 18.5 -> 0.8  // Bajo peso: menor absorción
            imc < 25.0 -> 1.0  // Normal: absorción estándar
            imc < 30.0 -> 1.2  // Sobrepeso: mayor absorción
            else -> 1.4        // Obeso: absorción significativamente mayor
        }

        // Ajustar por superficie corporal
        val sizeAdjustment = 1.8 / bsa.coerceIn(1.2, 2.5)

        // Factor de edad (asumiendo adulto promedio)
        val ageFactor = 1.0

        return absorptionFactor * imcAdjustment * sizeAdjustment * ageFactor
    }

    /**
     * Calcula los límites de exposición según el estándar seleccionado
     */
    fun calculateExposureLimits(standard: String): Double {
        return when (standard.uppercase()) {
            "FCC", "USA" -> FCC_LIMIT
            "ICNIRP", "EUROPA", "EU" -> ICNIRP_LIMIT
            else -> FCC_LIMIT // Default
        }
    }

    /**
     * Calcula el SAR (Tasa de Absorción Específica) para WiFi
     * Algoritmo corregido basado en intensidad de señal real
     */
    fun calculateWiFiSAR(signalStrengthDbm: Int, distance: Double, userAbsorptionFactor: Double): Double {
        // Convertir dBm a potencia en Watts (potencia recibida)
        val receivedPowerWatts = Math.pow(10.0, (signalStrengthDbm - 30.0) / 10.0) / 1000.0

        // Factor de distancia (atenuación por distancia inversa al cuadrado)
        val distanceFactor = 1.0 / (distance * distance)

        // Potencia efectiva considerando la atenuación
        val effectivePower = receivedPowerWatts * distanceFactor

        // Calcular SAR: SAR = (Potencia * FactorAbsorción) / PesoCorporal
        // FactorAbsorción ya incluye el peso corporal en su cálculo
        val rawSAR = effectivePower * userAbsorptionFactor * 1000 // Convertir a mW/kg y ajustar escala

        return rawSAR.coerceIn(0.01, 5.0) // Limitar a rango realista para WiFi
    }

    /**
     * Evalúa el nivel de riesgo basado en SAR y límites
     */
    fun evaluateRiskLevel(sarValue: Double, exposureLimit: Double): String {
        val ratio = sarValue / exposureLimit
        return when {
            ratio < 0.1 -> "semaphore_low"
            ratio < 0.5 -> "semaphore_moderate"
            ratio < 1.0 -> "semaphore_high"
            else -> "semaphore_critical"
        }
    }

    /**
     * Calcula el SAR para operadores móviles basado en intensidad de señal
     * Algoritmo corregido con física más precisa
     */
    fun calculateMobileSAR(signalStrengthDbm: Int, frequencyMHz: Double, userAbsorptionFactor: Double): Double {
        // Convertir dBm a potencia en Watts (potencia recibida en el dispositivo)
        val receivedPowerWatts = Math.pow(10.0, (signalStrengthDbm - 30.0) / 10.0) / 1000.0

        // Factor de frecuencia (SAR aumenta con frecuencia más alta debido a mayor absorción)
        val frequencyFactor = when {
            frequencyMHz < 1000 -> 1.0      // GSM 900
            frequencyMHz < 2000 -> 1.3      // GSM 1800 / LTE 1800
            frequencyMHz < 3000 -> 1.6      // LTE 2600
            else -> 2.0                      // 5G frequencies (mayor absorción)
        }

        // Factor de modulación y tipo de señal (señales móviles tienen características específicas)
        val modulationFactor = 0.7 // Factor para señales moduladas vs continuo

        // Calcular SAR usando el factor de absorción del usuario
        // SAR = Potencia_Recibida * Factor_Frecuencia * Factor_Modulación * Factor_Absorción_Usuario
        val rawSAR = receivedPowerWatts * frequencyFactor * modulationFactor * userAbsorptionFactor * 10000

        return rawSAR.coerceIn(0.01, 3.0) // Limitar a rango realista para móviles
    }

    /**
     * Calcula el SAR combinado de múltiples operadores
     * Algoritmo corregido considerando interferencia y límites biológicos
     */
    fun calculateCombinedMobileSAR(signalStrengths: List<Pair<Int, Double>>, userAbsorptionFactor: Double): Double {
        if (signalStrengths.isEmpty()) return 0.0

        // Calcular SAR individual para cada operador
        val individualSARs = signalStrengths.map { (signalStrength, frequency) ->
            calculateMobileSAR(signalStrength, frequency, userAbsorptionFactor)
        }

        // Encontrar el SAR máximo (el operador dominante)
        val maxSAR = individualSARs.maxOrNull() ?: 0.0

        // Calcular contribución de operadores adicionales con factor de atenuación
        // Las señales adicionales tienen menor impacto debido a interferencia y saturación biológica
        val additionalSAR = individualSARs.sum() - maxSAR

        // Factor de combinación: el operador principal aporta 100%, los adicionales aportan 30%
        val combinedSAR = maxSAR + (additionalSAR * 0.3)

        // Limitar por saturación biológica (el cuerpo no puede absorber más allá de ciertos límites)
        return combinedSAR.coerceIn(0.01, 4.0)
    }

    /**
     * Calcula el SAR para auriculares Bluetooth
     */
    fun calculateHeadsetSAR(deviceType: String, usageHours: Double): Double {
        val baseSAR = when (deviceType.lowercase()) {
            "bluetooth" -> 0.5
            "wireless" -> 0.8
            else -> 0.3
        }

        // Factor de uso diario
        val usageFactor = (usageHours / 24.0).coerceIn(0.0, 1.0)
        return baseSAR * usageFactor
    }

    /**
     * Obtiene recomendaciones basadas en el nivel de riesgo
     */
    fun getRecommendations(riskLevel: String): List<String> {
        return when (riskLevel.uppercase()) {
            "SEMAPHORE_LOW" -> listOf(
                "emf_rec_low_1",
                "emf_rec_low_2"
            )
            "SEMAPHORE_MODERATE" -> listOf(
                "emf_rec_moderate_1",
                "emf_rec_moderate_2",
                "emf_rec_moderate_3"
            )
            "SEMAPHORE_HIGH" -> listOf(
                "emf_rec_high_1",
                "emf_rec_high_2",
                "emf_rec_high_3"
            )
            "SEMAPHORE_CRITICAL" -> listOf(
                "emf_rec_critical_1",
                "emf_rec_critical_2",
                "emf_rec_critical_3"
            )
            else -> listOf("emf_rec_default")
        }
    }

    /**
     * Estima SAR por uso del teléfono (uplink) basado en indicadores indirectos.
     * - inCall incrementa duty cycle y potencia promedio cerca de cabeza.
     * - mobileTxKbps aproxima actividad de subida; mapea logarítmicamente.
     */
    fun estimatePhoneUseSAR(inCall: Boolean, mobileTxKbps: Double, userAbsorptionFactor: Double): Double {
        val base = if (inCall) 0.25 else 0.05 // W/kg base aproximado
        val txFactor = when {
            mobileTxKbps <= 1 -> 0.2
            mobileTxKbps <= 50 -> 0.5
            mobileTxKbps <= 200 -> 0.8
            else -> 1.0
        }
        val raw = (base * (0.5 + txFactor)) * (userAbsorptionFactor / 0.015)
        return raw.coerceIn(0.0, 1.5)
    }

    /**
     * Calcula el SAR (Tasa de Absorción Específica) para dispositivos Bluetooth
     * Considera la distancia, tipo de dispositivo y tiempo de conexión
     */
    fun calculateBluetoothSAR(deviceType: String, distance: Double, connectionTimeHours: Double, userAbsorptionFactor: Double): Double {
        // Potencia típica de dispositivos Bluetooth en dBm
        val basePowerDbm = when (deviceType.lowercase()) {
            "headset", "earbuds" -> -20 // Auriculares: ~10µW
            "speaker" -> -10 // Altavoz: ~100µW
            "watch", "fitness" -> -25 // Reloj: ~3µW
            "keyboard", "mouse" -> -30 // Teclado/ratón: ~1µW
            else -> -15 // Default: ~30µW
        }

        // Convertir dBm a Watts
        val powerWatts = Math.pow(10.0, (basePowerDbm - 30.0) / 10.0) / 1000.0

        // Factor de distancia (atenuación por distancia inversa al cuadrado)
        val distanceFactor = 1.0 / (distance * distance)

        // Factor de tiempo de conexión (exposición acumulada)
        val timeFactor = connectionTimeHours.coerceIn(0.0, 24.0) / 24.0

        // Potencia efectiva
        val effectivePower = powerWatts * distanceFactor * timeFactor

        // Calcular SAR considerando absorción del usuario
        val rawSAR = effectivePower * userAbsorptionFactor * 1000 // Convertir a mW/kg

        return rawSAR.coerceIn(0.001, 2.0) // Limitar a rango realista para Bluetooth
    }

    /**
     * Calcula el SAR combinado de múltiples dispositivos Bluetooth
     */
    fun calculateCombinedBluetoothSAR(bluetoothDevices: List<Triple<String, Double, Double>>, userAbsorptionFactor: Double): Double {
        if (bluetoothDevices.isEmpty()) return 0.0

        val totalSAR = bluetoothDevices.sumOf { (type, distance, hours) ->
            calculateBluetoothSAR(type, distance, hours, userAbsorptionFactor)
        }

        return totalSAR.coerceIn(0.0, 5.0) // Máximo combinado realista
    }
}
