package com.exposiguard.app.managers

import android.content.Context
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import kotlin.math.pow

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SARManager @Inject constructor(
    private val context: Context,
    private val wifiManager: WiFiManager,
    private val bluetoothManager: BluetoothManager,
    private val ambientExposureManager: AmbientExposureManager,
    private val physicalSensorManager: PhysicalSensorManager,
    private val healthManager: HealthManager,
    private val emfManager: EMFManager,
    private val userProfileManager: UserProfileManager,
    private val phoneUseManager: PhoneUseManager
) {

    data class SarBreakdown(
        val total: Double,
        val towers: Double,
        val phoneUse: Double,
        val wifi: Double,
        val bluetooth: Double,
        val ambient: Double,
        val magnetic: Double,
        val sleepCorrelation: Double
    )

    fun getCurrentSARLevel(): Double {
        return try {
            // Usar intensidades reales de celdas (dBm + frecuencia)
            val signalPairs = wifiManager.getCellSignalStrengthsWithFreq()

            if (signalPairs.isEmpty()) {
                return 0.1 // Valor mínimo si no hay redes detectadas
            }

            // Calcular SAR por torres (downlink proxy) + componente por uso del teléfono (uplink)
            val towerSar = calculateSARFromSignals(signalPairs)
            val useSar = estimatePhoneUseSar()
            val wifiSar = calculateWiFiSAR()
            val bluetoothSar = calculateBluetoothSAR()
            val ambientSar = calculateAmbientSAR()
            val magneticSar = calculateMagneticInterferenceSAR()
            val sleepCorrelationSar = kotlinx.coroutines.runBlocking { calculateSleepCorrelationSAR() }

            (towerSar + useSar + wifiSar + bluetoothSar + ambientSar + magneticSar + sleepCorrelationSar).coerceAtLeast(0.01)

        } catch (e: Exception) {
            // getString(R.string.comment_fallback_basic_calculation)
            0.5
        }
    }

    fun getCurrentSARBreakdown(): SarBreakdown {
        return try {
            val signalPairs = wifiManager.getCellSignalStrengthsWithFreq()
            val towers = calculateSARFromSignals(signalPairs).coerceAtLeast(0.0)
            val phone = estimatePhoneUseSar().coerceAtLeast(0.0)
            val wifi = calculateWiFiSAR().coerceAtLeast(0.0)
            val bluetooth = calculateBluetoothSAR().coerceAtLeast(0.0)
            val ambient = calculateAmbientSAR().coerceAtLeast(0.0)
            val magnetic = calculateMagneticInterferenceSAR().coerceAtLeast(0.0)
            val sleepCorrelation = kotlinx.coroutines.runBlocking { calculateSleepCorrelationSAR() }.coerceAtLeast(0.0)
            val total = (towers + phone + wifi + bluetooth + ambient + magnetic + sleepCorrelation).coerceAtLeast(0.01)
            SarBreakdown(total = total, towers = towers, phoneUse = phone, wifi = wifi, bluetooth = bluetooth, ambient = ambient, magnetic = magnetic, sleepCorrelation = sleepCorrelation)
        } catch (_: Exception) {
            SarBreakdown(total = 0.5, towers = 0.3, phoneUse = 0.2, wifi = 0.0, bluetooth = 0.0, ambient = 0.0, magnetic = 0.0, sleepCorrelation = 0.0)
        }
    }

    private fun calculateSARFromSignals(signalStrengths: List<Pair<Int, Double>>): Double {
        if (signalStrengths.isEmpty()) return 0.3

        // Obtener perfil de usuario desde almacenamiento local
        val userProfile = userProfileManager.getUserProfile()

        // Obtener el estándar seleccionado desde SharedPreferences
        val sharedPreferences = context.getSharedPreferences("exposiguard_prefs", android.content.Context.MODE_PRIVATE)
        val selectedStandard = sharedPreferences.getString("exposure_standard", "FCC (USA)") ?: "FCC (USA)"

        // Convertir el estándar a formato que entiende EMFManager
        val standard = when {
            selectedStandard.contains("FCC") -> "FCC"
            selectedStandard.contains("ICNIRP") -> "ICNIRP"
            else -> "FCC"
        }

        val absorptionFactor = emfManager.calculateAbsorptionFactor(userProfile)

        // Calcular SAR combinado considerando el estándar
    val rawSAR = emfManager.calculateCombinedMobileSAR(signalStrengths, absorptionFactor)

        // Aplicar límite según el estándar seleccionado
        val limit = emfManager.calculateExposureLimits(standard)
        return rawSAR.coerceIn(0.01, limit * 1.2) // Máximo 120% del límite del estándar
    }

    private fun estimatePhoneUseSar(): Double {
        val userProfile = userProfileManager.getUserProfile()
        val heightM = (userProfile.height / 100.0).coerceAtLeast(0.01)
        val bmi = (userProfile.weight / (heightM * heightM))
        val absorptionFactor = emfManager.calculateAbsorptionFactor(
            EMFManager.UserProfile(userProfile.weight, userProfile.height, bmi)
        )
        val ind = phoneUseManager.getIndicators()
        return emfManager.estimatePhoneUseSAR(ind.inCall, ind.mobileTxKbps, absorptionFactor)
    }

    // Eliminadas utilidades de parsing por nombres: ahora usamos APIs de señal reales

    fun getCurrentSARLevelWithUserData(weight: Double, height: Double, standard: String): Double {
        return try {
            val signalPairs = wifiManager.getCellSignalStrengthsWithFreq()

            if (signalPairs.isEmpty()) {
                return 0.1
            }

        val userProfile = EMFManager.UserProfile(weight, height)
            val absorptionFactor = emfManager.calculateAbsorptionFactor(userProfile)

        val rawSAR = emfManager.calculateCombinedMobileSAR(signalPairs, absorptionFactor) +
            emfManager.estimatePhoneUseSAR(phoneUseManager.getIndicators().inCall, phoneUseManager.getIndicators().mobileTxKbps, absorptionFactor)

            // Normalizar estándar de entrada (p.ej. "FCC (USA)" -> "FCC")
            val normalizedStandard = when {
                standard.contains("FCC", ignoreCase = true) -> "FCC"
                standard.contains("ICNIRP", ignoreCase = true) -> "ICNIRP"
                else -> "FCC"
            }
            val limit = emfManager.calculateExposureLimits(normalizedStandard)
            rawSAR.coerceIn(0.01, limit * 1.2)

        } catch (e: Exception) {
            0.5
        }
    }

    private fun calculateWiFiSAR(): Double {
        return try {
            val userProfile = userProfileManager.getUserProfile()
            val absorptionFactor = emfManager.calculateAbsorptionFactor(
                EMFManager.UserProfile(userProfile.weight, userProfile.height, userProfile.weight / ((userProfile.height / 100.0).pow(2)))
            )

            // Obtener redes WiFi detectadas
            val wifiNetworks = wifiManager.getWiFiNetworks()
            if (wifiNetworks.isEmpty()) return 0.0

            // Calcular SAR promedio de todas las redes WiFi
            val totalWiFiSAR = wifiNetworks.sumOf { network ->
                // Estimar distancia basada en intensidad de señal (aproximación)
                val distance = when {
                    network.level >= -30 -> 1.0 // Muy cerca
                    network.level >= -50 -> 3.0 // Cerca
                    network.level >= -70 -> 10.0 // Media distancia
                    else -> 30.0 // Lejos
                }
                emfManager.calculateWiFiSAR(network.level, distance, absorptionFactor)
            }

            totalWiFiSAR / wifiNetworks.size // Promedio
        } catch (e: Exception) {
            0.0
        }
    }

    private fun calculateBluetoothSAR(): Double {
        return try {
            val userProfile = userProfileManager.getUserProfile()
            val absorptionFactor = emfManager.calculateAbsorptionFactor(
                EMFManager.UserProfile(userProfile.weight, userProfile.height, userProfile.weight / ((userProfile.height / 100.0).pow(2)))
            )

            // Obtener dispositivos Bluetooth conectados
            val bluetoothDevices = bluetoothManager.getPairedDevices()
            if (bluetoothDevices.isEmpty()) return 0.0

            // Crear lista de dispositivos con estimaciones de tipo, distancia y tiempo
            val deviceData = bluetoothDevices.map { device ->
                // Estimar tipo de dispositivo basado en nombre; proteger acceso si falta BLUETOOTH_CONNECT
                val safeName = runCatching { device.name }.getOrNull()
                val deviceType = when {
                    safeName?.contains("headset", ignoreCase = true) == true ||
                    safeName?.contains("earbuds", ignoreCase = true) == true ||
                    safeName?.contains("buds", ignoreCase = true) == true -> "headset"
                    safeName?.contains("speaker", ignoreCase = true) == true -> "speaker"
                    safeName?.contains("watch", ignoreCase = true) == true -> "watch"
                    safeName?.contains("keyboard", ignoreCase = true) == true -> "keyboard"
                    safeName?.contains("mouse", ignoreCase = true) == true -> "mouse"
                    else -> "unknown"
                }

                // Estimar distancia (asumir cercana para dispositivos conectados)
                val distance = 0.5 // 50cm aproximado
                // Estimar tiempo de conexión (aproximación conservadora)
                val connectionTime = 2.0 // 2 horas promedio por día

                Triple(deviceType, distance, connectionTime)
            }

            emfManager.calculateCombinedBluetoothSAR(deviceData, absorptionFactor)
        } catch (e: Exception) {
            0.0
        }
    }

    private fun calculateAmbientSAR(): Double {
        return try {
            val ambientSummary = ambientExposureManager.getAmbientSummary()
            if (ambientSummary == null) return 0.0

            // Convertir densidad de potencia a SAR aproximado
            // SAR ≈ Densidad de Potencia × Factor de Absorción × Tiempo de Exposición
            val userProfile = userProfileManager.getUserProfile()
            val absorptionFactor = emfManager.calculateAbsorptionFactor(
                EMFManager.UserProfile(userProfile.weight, userProfile.height, userProfile.weight / ((userProfile.height / 100.0).pow(2)))
            )

            // Factor de tiempo (asumir exposición continua durante 1 hora para cálculo diario)
            val timeFactor = 1.0 / 24.0 // 1 hora de 24

            // Calcular SAR basado en densidad de potencia total
            val rawSAR = ambientSummary.totalDensityWPerM2 * absorptionFactor * timeFactor * 1000

            rawSAR.coerceIn(0.0, 2.0) // Limitar a valores realistas
        } catch (e: Exception) {
            0.0
        }
    }

    private fun calculateMagneticInterferenceSAR(): Double {
        return try {
            val interferenceData = physicalSensorManager.getCurrentEMInterference()

            // Convertir nivel de interferencia a SAR
            // Interferencia magnética alta puede indicar presencia de campos EM fuertes
            val baseSAR = when (interferenceData.level) {
                PhysicalSensorManager.InterferenceLevel.NONE -> 0.0
                PhysicalSensorManager.InterferenceLevel.LOW -> 0.05
                PhysicalSensorManager.InterferenceLevel.MODERATE -> 0.15
                PhysicalSensorManager.InterferenceLevel.HIGH -> 0.3
                PhysicalSensorManager.InterferenceLevel.CRITICAL -> 0.5
            }

            // Ajustar basado en la magnitud del campo magnético
            val magneticField = physicalSensorManager.getCurrentMagneticField()
            val fieldAdjustment = (magneticField.magnitude / 50.0).coerceIn(0.5, 2.0) // Normalizar alrededor del campo terrestre

            baseSAR * fieldAdjustment
        } catch (e: Exception) {
            0.0
        }
    }

    private suspend fun calculateSleepCorrelationSAR(): Double {
        return try {
            // Obtener datos de sueño recientes (últimas 24 horas)
            val sleepData = healthManager.getSleepQualityData()

            // Obtener historial de radiación de las últimas horas
            // En una implementación real, esto vendría de un repositorio de datos históricos
            val recentRadiation = listOf(0.3, 0.4, 0.2, 0.5) // Simulado

            // Calcular correlación
            val correlation = healthManager.analyzeRadiationSleepCorrelation(recentRadiation, sleepData)

            // Convertir impacto negativo en sueño a componente SAR adicional
            val sleepImpactSAR = if (correlation.sleepQualityImpact < 0) {
                Math.abs(correlation.sleepQualityImpact) * 0.01 // Convertir puntos de calidad a SAR
            } else {
                0.0
            }

            sleepImpactSAR.coerceIn(0.0, 0.3) // Limitar impacto
        } catch (e: Exception) {
            0.0
        }
    }

    // Eliminado: no generar lecturas SAR sintéticas. Usar getCurrentSARLevel() en tiempo real
}
