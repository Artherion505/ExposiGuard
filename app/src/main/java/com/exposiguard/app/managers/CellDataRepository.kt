package com.exposiguard.app.managers

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import com.exposiguard.app.data.NetworkData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CellDataRepository {
    private val _networkDataFlow = MutableStateFlow(NetworkData())
    val networkDataFlow: StateFlow<NetworkData> = _networkDataFlow.asStateFlow()

    private var isStarted = false

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (isStarted) return
        isStarted = true

        CoroutineScope(Dispatchers.IO).launch {
            val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val persistentNetworkData = NetworkData()

            while (isActive) {
                try {
                    // Usar TelephonyManager básico en lugar de NetMonster
                    @Suppress("DEPRECATION")
                    val nt = manager.networkType
                    persistentNetworkData.networkType = getNetworkTypeString(nt)
                    persistentNetworkData.carrierName = manager.networkOperatorName ?: "Operador desconocido"
                    persistentNetworkData.operatorCode = manager.networkOperator ?: ""

                    if (isAirplaneModeOn(context)) {
                        persistentNetworkData.isAirplaneEnabled = true
                    } else {
                        persistentNetworkData.isAirplaneEnabled = false
                    }

                    // Información básica de señal
                    persistentNetworkData.signalStrength = getSignalStrengthInfo(manager)

                    _networkDataFlow.value = persistentNetworkData.copy()

                } catch (e: Exception) {
                    Log.e("CellDataRepository", "Error getting network info", e)
                }
                delay(2000L) // Actualizar cada 2 segundos
            }
        }
    }

    private fun getNetworkTypeString(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS (2G)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE (2G)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS (3G)"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA (3G)"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA (3G)"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE (4G)"
            TelephonyManager.NETWORK_TYPE_NR -> "NR (5G)"
            else -> "Desconocido"
        }
    }

    private fun getSignalStrengthInfo(manager: TelephonyManager): String {
        return try {
            // Intentar obtener información detallada de intensidad de señal
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val cellInfoList = manager.allCellInfo
                if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                    val signalInfo = StringBuilder()

                    for (cellInfo in cellInfoList.take(3)) { // Limitar a las 3 primeras celdas para no sobrecargar
                        when (cellInfo) {
                            is android.telephony.CellInfoLte -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                signalInfo.append("LTE: ${signalStrength.dbm}dBm (RSRP: ${signalStrength.rsrp}dBm)")
                                break // Usar la primera celda LTE disponible
                            }
                            is android.telephony.CellInfoGsm -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                signalInfo.append("GSM: ${signalStrength.dbm}dBm")
                                break
                            }
                            is android.telephony.CellInfoWcdma -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                signalInfo.append("3G: ${signalStrength.dbm}dBm")
                                break
                            }
                        }
                    }

                    if (signalInfo.isNotEmpty()) {
                        return signalInfo.toString()
                    }
                }
            }

            // Fallback a información básica
            "Información básica disponible"
        } catch (e: SecurityException) {
            "Permisos insuficientes"
        } catch (e: Exception) {
            "No disponible"
        }
    }

    private fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }

    fun stop() {
        isStarted = false
    }
}
