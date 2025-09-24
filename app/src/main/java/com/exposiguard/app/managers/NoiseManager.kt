package com.exposiguard.app.managers

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoiseManager @Inject constructor(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null

    fun startNoiseMonitoring(): Boolean {
        return try {
            // Verificar si ya hay un MediaRecorder activo
            if (mediaRecorder != null) {
                stopNoiseMonitoring()
            }

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile("/dev/null")
                prepare()
                start()
            }
            android.util.Log.d("NoiseManager", "Noise monitoring started successfully")
            true
        } catch (e: Exception) {
            android.util.Log.e("NoiseManager", "Failed to start noise monitoring: ${e.message}", e)
            // Limpiar el MediaRecorder si falló
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }

    fun stopNoiseMonitoring() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
    }

    fun getCurrentNoiseLevel(): Double {
        return try {
            mediaRecorder?.maxAmplitude?.toDouble() ?: 0.0
        } catch (e: Exception) {
            android.util.Log.e("NoiseManager", "Error getting noise level: ${e.message}", e)
            0.0
        }
    }
}
