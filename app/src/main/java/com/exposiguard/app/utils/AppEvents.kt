package com.exposiguard.app.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppEvents {
    sealed class Event {
        object SettingsChanged : Event()
        object DataChanged : Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 32)
    val events = _events.asSharedFlow()

    fun emit(event: Event) {
        _events.tryEmit(event)
    }

    /**
     * Obtiene el intervalo de monitoreo configurado en minutos desde SharedPreferences
     * @param context Contexto de la aplicación
     * @return Intervalo en minutos (por defecto 5 minutos)
     */
    fun getMonitoringIntervalMinutes(context: Context): Long {
        val prefs = context.getSharedPreferences("exposiguard_settings", Context.MODE_PRIVATE)
        val intervalString = prefs.getString("monitoring_interval", "5") ?: "5"
        return intervalString.toLongOrNull() ?: 5L
    }

    /**
     * Obtiene el intervalo de monitoreo configurado en milisegundos
     * @param context Contexto de la aplicación
     * @return Intervalo en milisegundos
     */
    fun getMonitoringIntervalMillis(context: Context): Long {
        return getMonitoringIntervalMinutes(context) * 60 * 1000
    }

    /**
     * Ventana de promediado para el monitoreo continuo (Home/semáforo).
     * Se deriva del intervalo configurado, usando reglas amigables:
     * 1 min -> 10 min, 5 min -> 30 min, 10 min -> 60 min, 30 min -> 6 h, 60 min -> 12 h, fallback 60 min.
     */
    fun getAveragingWindowMillis(context: Context): Long {
        val prefs = context.getSharedPreferences("exposiguard_settings", Context.MODE_PRIVATE)
        val explicit = prefs.getString("averaging_window_min", null)?.toLongOrNull()
        val windowMin = explicit ?: run {
            val intervalMin = getMonitoringIntervalMinutes(context)
            when (intervalMin) {
                1L -> 10L
                5L -> 30L
                10L -> 60L
                30L -> 6L * 60L
                60L -> 12L * 60L
                else -> 60L
            }
        }
        return windowMin * 60_000L
    }
}
