package com.exposiguard.app.managers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager para sensores físicos del dispositivo
 * Incluye magnetómetro para detectar campos magnéticos ambientales
 * que pueden indicar interferencias electromagnéticas
 */
@Singleton
class PhysicalSensorManager @Inject constructor(
    private val context: Context
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // Magnetómetro para detectar campos magnéticos
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    // Acelerómetro para detectar movimiento
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // Giroscopio para detectar rotación
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Estado de los sensores
    private val _magneticField = MutableStateFlow(MagneticFieldData(0f, 0f, 0f, 0f))
    val magneticField: StateFlow<MagneticFieldData> = _magneticField

    private val _deviceMotion = MutableStateFlow(DeviceMotion(0f, 0f, 0f, MotionType.STATIONARY))
    val deviceMotion: StateFlow<DeviceMotion> = _deviceMotion

    private val _emInterference = MutableStateFlow(EMInterferenceData(0.0, InterferenceLevel.NONE))
    val emInterference: StateFlow<EMInterferenceData> = _emInterference

    // Datos del sensor de magnetómetro
    data class MagneticFieldData(
        val x: Float, // Microteslas
        val y: Float,
        val z: Float,
        val magnitude: Float // Magnitud total del campo
    )

    // Datos de movimiento del dispositivo
    data class DeviceMotion(
        val acceleration: Float,
        val rotationSpeed: Float,
        val orientation: Float,
        val motionType: MotionType
    )

    // Datos de interferencia electromagnética detectada
    data class EMInterferenceData(
        val interferenceLevel: Double, // 0-100
        val level: InterferenceLevel
    )

    enum class MotionType {
        STATIONARY, WALKING, RUNNING, VEHICLE
    }

    enum class InterferenceLevel {
        NONE, LOW, MODERATE, HIGH, CRITICAL
    }

    init {
        startSensorMonitoring()
    }

    /**
     * Inicia el monitoreo de sensores físicos
     */
    fun startSensorMonitoring() {
        try {
            // Registrar magnetómetro
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("PhysicalSensorManager", "Magnetómetro registrado")
            }

            // Registrar acelerómetro
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("PhysicalSensorManager", "Acelerómetro registrado")
            }

            // Registrar giroscopio
            gyroscope?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
                Log.d("PhysicalSensorManager", "Giroscopio registrado")
            }

        } catch (e: Exception) {
            Log.e("PhysicalSensorManager", "Error al registrar sensores: ${e.message}")
        }
    }

    /**
     * Detiene el monitoreo de sensores
     */
    fun stopSensorMonitoring() {
        sensorManager.unregisterListener(this)
        Log.d("PhysicalSensorManager", "Sensores detenidos")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                handleMagneticFieldData(event)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                handleAccelerometerData(event)
            }
            Sensor.TYPE_GYROSCOPE -> {
                handleGyroscopeData(event)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("PhysicalSensorManager", "Precisión del sensor ${sensor?.name} cambió a: $accuracy")
    }

    /**
     * Procesa datos del magnetómetro para detectar interferencias EM
     */
    private fun handleMagneticFieldData(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calcular magnitud del campo magnético
        val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        val magneticData = MagneticFieldData(x, y, z, magnitude)
        _magneticField.value = magneticData

        // Analizar interferencias electromagnéticas basadas en el campo magnético
        analyzeEMInterference(magnitude)
    }

    /**
     * Procesa datos del acelerómetro para detectar movimiento
     */
    private fun handleAccelerometerData(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calcular aceleración total (excluyendo gravedad)
        val acceleration = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat() - 9.81f

        // Determinar tipo de movimiento
        val motionType = when {
            Math.abs(acceleration) < 0.5 -> MotionType.STATIONARY
            Math.abs(acceleration) < 2.0 -> MotionType.WALKING
            Math.abs(acceleration) < 5.0 -> MotionType.RUNNING
            else -> MotionType.VEHICLE
        }

        val motionData = DeviceMotion(
            acceleration = Math.abs(acceleration),
            rotationSpeed = 0f, // Se actualizará con giroscopio
            orientation = 0f,   // Se puede calcular con magnetómetro
            motionType = motionType
        )

        _deviceMotion.value = motionData
    }

    /**
     * Procesa datos del giroscopio para detectar rotación
     */
    private fun handleGyroscopeData(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calcular velocidad de rotación
        val rotationSpeed = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()

        // Actualizar datos de movimiento con velocidad de rotación
        val currentMotion = _deviceMotion.value
        _deviceMotion.value = currentMotion.copy(rotationSpeed = rotationSpeed)
    }

    /**
     * Analiza el campo magnético para detectar interferencias EM
     */
    private fun analyzeEMInterference(magneticMagnitude: Float) {
        // Campo magnético terrestre típico: ~25-65 μT
        // Valores significativamente diferentes pueden indicar interferencias EM
        val baseEarthField = 50f // μT (valor promedio del campo terrestre)

        // Calcular desviación del campo magnético normal
        val deviation = Math.abs(magneticMagnitude - baseEarthField)

        // Nivel de interferencia basado en la desviación
        val interferenceLevel = when {
            deviation < 5 -> 0.0  // Sin interferencia
            deviation < 15 -> 25.0 // Baja interferencia
            deviation < 30 -> 50.0 // Interferencia moderada
            deviation < 50 -> 75.0 // Alta interferencia
            else -> 100.0 // Interferencia crítica
        }

        val level = when {
            interferenceLevel < 25 -> InterferenceLevel.NONE
            interferenceLevel < 50 -> InterferenceLevel.LOW
            interferenceLevel < 75 -> InterferenceLevel.MODERATE
            interferenceLevel < 90 -> InterferenceLevel.HIGH
            else -> InterferenceLevel.CRITICAL
        }

        _emInterference.value = EMInterferenceData(interferenceLevel, level)
    }

    /**
     * Obtiene el nivel actual de interferencia EM
     */
    fun getCurrentEMInterference(): EMInterferenceData {
        return _emInterference.value
    }

    /**
     * Obtiene datos actuales del campo magnético
     */
    fun getCurrentMagneticField(): MagneticFieldData {
        return _magneticField.value
    }

    /**
     * Obtiene datos actuales de movimiento del dispositivo
     */
    fun getCurrentDeviceMotion(): DeviceMotion {
        return _deviceMotion.value
    }

    /**
     * Verifica si los sensores necesarios están disponibles
     */
    fun areSensorsAvailable(): Boolean {
        return magnetometer != null && accelerometer != null
    }

    /**
     * Obtiene información detallada sobre sensores disponibles
     */
    fun getSensorInfo(): Map<String, Boolean> {
        return mapOf(
            "Magnetómetro" to (magnetometer != null),
            "Acelerómetro" to (accelerometer != null),
            "Giroscopio" to (gyroscope != null)
        )
    }
}