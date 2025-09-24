package com.exposiguard.app.managers

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

@Singleton
class NoiseManager @Inject constructor(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null
    private var currentAmplitude = 0.0

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE = 4096
    }

    fun startNoiseMonitoring(): Boolean {
        return try {
            android.util.Log.d("NoiseManager", "Attempting to start noise monitoring with AudioRecord...")

            // Verificar si ya hay un AudioRecord activo
            if (audioRecord != null && isRecording.get()) {
                android.util.Log.d("NoiseManager", "Stopping existing AudioRecord...")
                stopNoiseMonitoring()
            }

            // Calcular el tamaño mínimo del buffer
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = if (minBufferSize != AudioRecord.ERROR && minBufferSize != AudioRecord.ERROR_BAD_VALUE) {
                minBufferSize.coerceAtLeast(BUFFER_SIZE)
            } else {
                BUFFER_SIZE
            }

            android.util.Log.d("NoiseManager", "Creating AudioRecord with buffer size: $bufferSize")

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                android.util.Log.e("NoiseManager", "Failed to initialize AudioRecord")
                cleanup()
                return false
            }

            android.util.Log.d("NoiseManager", "Starting AudioRecord...")
            audioRecord?.startRecording()
            isRecording.set(true)

            // Iniciar hilo de lectura de audio
            recordingThread = thread(start = true) {
                recordAudio()
            }

            android.util.Log.d("NoiseManager", "Noise monitoring started successfully with AudioRecord")
            true
        } catch (e: Exception) {
            android.util.Log.e("NoiseManager", "Failed to start noise monitoring: ${e.message}", e)
            android.util.Log.e("NoiseManager", "Exception type: ${e.javaClass.simpleName}")
            cleanup()
            false
        }
    }

    fun stopNoiseMonitoring() {
        android.util.Log.d("NoiseManager", "Stopping noise monitoring...")
        isRecording.set(false)
        cleanup()
        // Forzar reseteo completo del estado de ruido
        currentAmplitude = -96.0
        android.util.Log.d("NoiseManager", "Noise monitoring stopped and amplitude reset to -96 dB")
    }

    private fun cleanup() {
        try {
            recordingThread?.join(1000) // Esperar máximo 1 segundo
            recordingThread = null
        } catch (e: Exception) {
            android.util.Log.w("NoiseManager", "Error joining recording thread: ${e.message}")
        }

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            android.util.Log.w("NoiseManager", "Error stopping AudioRecord: ${e.message}")
        }

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            android.util.Log.w("NoiseManager", "Error releasing AudioRecord: ${e.message}")
        }

        audioRecord = null
        currentAmplitude = 0.0
        android.util.Log.d("NoiseManager", "AudioRecord cleanup completed")
    }

    private fun recordAudio() {
        val audioBuffer = ShortArray(BUFFER_SIZE / 2) // Para 16-bit PCM
        var silenceCounter = 0
        val maxSilenceCounter = 5 // Después de 5 lecturas silenciosas, resetear (más agresivo)

        android.util.Log.d("NoiseManager", "Audio recording thread started")

        while (isRecording.get()) {
            try {
                val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0

                if (readResult > 0) {
                    // Calcular RMS (Root Mean Square) para obtener la amplitud
                    var sum = 0.0
                    var validSamples = 0

                    for (i in 0 until readResult) {
                        val sample = audioBuffer[i]
                        // Umbral más agresivo para filtrar ruido de fondo
                        if (kotlin.math.abs(sample.toInt()) > 50) { // Aumentado de 10 a 50
                            sum += (sample * sample).toDouble()
                            validSamples++
                        }
                    }

                    if (validSamples > 0) {
                        val rms = sqrt(sum / validSamples)
                        // Convertir a decibeles (dB) con mejor precisión
                        currentAmplitude = if (rms > 0) {
                            20 * log10(rms / Short.MAX_VALUE)
                        } else {
                            -96.0 // Silencio
                        }
                        silenceCounter = 0 // Resetear contador de silencio
                        android.util.Log.d("NoiseManager", "Valid noise level: ${currentAmplitude} dB (rms: $rms, validSamples: $validSamples)")
                    } else {
                        // Todas las muestras están por debajo del umbral
                        silenceCounter++
                        if (silenceCounter >= maxSilenceCounter) {
                            currentAmplitude = -96.0 // Forzar silencio después de varias lecturas silenciosas
                            silenceCounter = 0
                            android.util.Log.d("NoiseManager", "Silence detected, resetting amplitude to -96 dB")
                        }
                        android.util.Log.d("NoiseManager", "No valid samples detected (silence), counter: $silenceCounter")
                    }
                } else if (readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                    android.util.Log.e("NoiseManager", "AudioRecord read error: invalid operation")
                    break
                } else if (readResult == AudioRecord.ERROR_BAD_VALUE) {
                    android.util.Log.e("NoiseManager", "AudioRecord read error: bad value")
                    break
                }
            } catch (e: Exception) {
                android.util.Log.e("NoiseManager", "Error reading audio data: ${e.message}", e)
                break
            }
        }

        android.util.Log.d("NoiseManager", "Audio recording thread finished")
    }

    fun getCurrentNoiseLevel(): Double {
        return try {
            if (!isRecording.get()) {
                android.util.Log.w("NoiseManager", "AudioRecord is not recording, returning 0.0")
                return 0.0
            }

            // Para depuración: si el valor es muy alto, forzar a cero inicialmente
            if (currentAmplitude > -10.0) { // Si es mayor a -10dB, probablemente es ruido residual
                android.util.Log.d("NoiseManager", "High noise level detected (${currentAmplitude} dB), forcing to silence")
                currentAmplitude = -96.0
            }

            // Limitar el valor de dB a un rango razonable
            val clampedDb = currentAmplitude.coerceIn(-96.0, 0.0)

            // Convertir de dB a un valor positivo escalado para compatibilidad con la interfaz
            // Los valores de dB van de -96 (silencio) a 0 (muy alto)
            // Los convertimos a un rango de 0-32767 para compatibilidad con MediaRecorder.maxAmplitude
            val scaledValue = ((clampedDb + 96.0) / 96.0 * 32767.0).coerceIn(0.0, 32767.0)

            android.util.Log.d("NoiseManager", "Noise level: ${currentAmplitude} dB -> $clampedDb dB (clamped) -> $scaledValue (scaled)")
            scaledValue
        } catch (e: Exception) {
            android.util.Log.e("NoiseManager", "Error getting noise level: ${e.message}", e)
            0.0
        }
    }

    fun isMonitoringActive(): Boolean {
        return isRecording.get() && audioRecord != null
    }
}
