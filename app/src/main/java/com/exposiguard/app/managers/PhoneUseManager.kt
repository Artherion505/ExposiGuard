package com.exposiguard.app.managers

import android.content.Context
import android.net.TrafficStats
import android.telephony.TelephonyManager
import android.os.Build
import android.telephony.TelephonyCallback
import javax.inject.Inject

/**
 * PhoneUseManager
 * Estima indicadores de uso del teléfono que correlacionan con emisión uplink:
 * - Estado de llamada (en_lamada)
 * - Tasa de subida móvil (kbps) basada en TrafficStats
 */
class PhoneUseManager @Inject constructor(private val context: Context) {

    private var lastTxBytes: Long = TrafficStats.getMobileTxBytes()
    private var lastTs: Long = System.currentTimeMillis()
    private var lastCallState: Int = TelephonyManager.CALL_STATE_IDLE
    private var telephonyCallback: TelephonyCallback? = null

    init {
        // Registrar callback moderno para estado de llamada en Android 12+ (API 31)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        lastCallState = state
                    }
                }
                telephonyCallback = cb
                tm.registerTelephonyCallback(context.mainExecutor, cb)
            } catch (_: SecurityException) {
                // Falta permiso READ_PHONE_STATE: continuamos con estado por defecto
            } catch (_: Exception) {
                // Silencioso: fallback en getIndicators
            }
        }
    }

    data class Indicators(
        val inCall: Boolean,
        val mobileTxKbps: Double
    )

    fun getIndicators(): Indicators {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val state = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Usar el último estado recibido por callback
            lastCallState
        } else {
            // Fallback legacy para APIs antiguas
            @Suppress("DEPRECATION")
            try { tm.callState } catch (_: SecurityException) { TelephonyManager.CALL_STATE_IDLE }
        }
        val inCall = state == TelephonyManager.CALL_STATE_OFFHOOK || state == TelephonyManager.CALL_STATE_RINGING

        val now = System.currentTimeMillis()
        val tx = TrafficStats.getMobileTxBytes()
        val dt = (now - lastTs).coerceAtLeast(1L) // ms
        val dBytes = (tx - lastTxBytes).coerceAtLeast(0L)
        val kbps = (dBytes * 8.0) / dt // kbit/ms => effectively kbps since 1 ms denominator
        // Normalizar a kbps (ms -> s): multiplicar por 1000/1000 cancela; nuestra fórmula ya está en kbps

        lastTs = now
        lastTxBytes = tx

        return Indicators(inCall = inCall, mobileTxKbps = kbps)
    }
}
