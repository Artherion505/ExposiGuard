@file:Suppress("DEPRECATION")
package com.exposiguard.app.managers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.location.LocationManager
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.CellInfo
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.exposiguard.app.R
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.exposiguard.app.data.WiFiNetwork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class WiFiManager @Inject constructor(
    private val context: Context,
    private val userProfileManager: UserProfileManager
) {

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private val _detectedNetworks = MutableStateFlow<List<WiFiNetwork>>(emptyList())
    val detectedNetworks: StateFlow<List<WiFiNetwork>> = _detectedNetworks

    private val _totalExposure = MutableStateFlow(0.0)
    val totalExposure: StateFlow<Double> = _totalExposure

    private val _carrierNetworks = MutableStateFlow<List<WiFiNetwork>>(emptyList())
    val carrierNetworks: StateFlow<List<WiFiNetwork>> = _carrierNetworks

    private val _bluetoothDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bluetoothDevices: StateFlow<List<BluetoothDevice>> = _bluetoothDevices

    // Flujo para información de operador en tiempo real
    private val _carrierInfoFlow = MutableStateFlow<String?>(null)
    val carrierInfoFlow: StateFlow<String?> = _carrierInfoFlow

    // Callback/Listener de telefonía
    private var isTelephonyRegistered = false
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    // Mantener referencia para API 31+ (TelephonyCallback) sin romper en APIs bajas
    private var telephonyCallbackApi31: Any? = null

    // Verificar si tenemos permisos necesarios
    fun hasLocationPermission(): Boolean {
        // Aceptar FINE o COARSE como válidos para lectura de scanResults en < Android 13
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // En versiones anteriores, BLUETOOTH cubre operaciones básicas como nombre/clase del dispositivo
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            // Fallback best-effort para APIs antiguas
            val gps = runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
            val net = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
            gps || net
        }
    }

    // Redes WiFi de compañías telefónicas conocidas
    private val carrierSSIDs = setOf(
        // España
        "MovistarWiFi", "ONO WiFi", "Jazztel WiFi", "Vodafone WiFi", "Orange WiFi",
        "Yoigo WiFi", "MasMovil WiFi", "Pepephone WiFi",
        // Internacional
        "Telekom", "Vodafone", "Orange", "T-Mobile", "AT&T", "Verizon",
        "Sprint", "BT WiFi", "Free WiFi", "SFR WiFi", "TIM WiFi",
        // Más operadores comunes
        "Movistar", "Claro WiFi", "Telcel WiFi", "ATT WiFi", "Comcast",
        "Spectrum WiFi", "Cox WiFi", "Optimum WiFi", "CenturyLink",
        "Windstream", "Frontier WiFi", "HughesNet", "Starlink"
    )

    // Dispositivos Bluetooth de audio
    private val audioDeviceClasses = setOf(
        1024, // AUDIO_VIDEO_HEADPHONES
        1028, // AUDIO_VIDEO_HANDSFREE
        1032, // AUDIO_VIDEO_HEADSETS
        1044, // AUDIO_VIDEO_WEARABLE_HEADSET
        1048  // AUDIO_VIDEO_HIFI_AUDIO
    )

    @Suppress("DEPRECATION")
    fun getWiFiNetworks(): List<WiFiNetwork> {
        // A partir de Android 13 se puede usar NEARBY_WIFI_DEVICES; si no, requerimos ubicación fina
    val hasNearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else false

    if (!hasLocationPermission() && !hasNearbyWifi) {
            return emptyList()
        }

        if (!wifiManager.isWifiEnabled) {
            return emptyList()
        }

        return try {
            val scanResults = wifiManager.scanResults
            val networks = scanResults.map { result ->
                @Suppress("DEPRECATION")
                val ssidVal = result.SSID ?: ""
                WiFiNetwork(
                    ssid = ssidVal,
                    bssid = result.BSSID ?: "",
                    frequency = result.frequency,
                    level = result.level,
                    capabilities = result.capabilities
                )
            }
            _detectedNetworks.value = networks

            // Filtrar redes de compañías telefónicas con mejor detección
            val carriers = networks.filter { network ->
                val ssid = network.ssid.lowercase()
                carrierSSIDs.any { carrierSSID ->
                    // Búsqueda más flexible
                    ssid.contains(carrierSSID.lowercase()) ||
                    carrierSSID.lowercase().contains(ssid) ||
                    // Detectar patrones comunes
                    ssid.contains("wifi") && (
                        ssid.contains("movistar") || ssid.contains("vodafone") ||
                        ssid.contains("orange") || ssid.contains("telekom") ||
                        ssid.contains("telefonica") || ssid.contains("claro") ||
                        ssid.contains("telcel") || ssid.contains("att")
                    )
                }
            }
            _carrierNetworks.value = carriers

            networks
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            // getString(R.string.comment_handle_unexpected_error)
            emptyList()
        }
    }

    /**
     * Inicia un escaneo de redes WiFi
     */
    @Suppress("DEPRECATION")
    fun startWifiScan(): Boolean {
        return try {
            val hasNearbyWifi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            } else false

            if (!hasLocationPermission() && !hasNearbyWifi) {
                android.util.Log.w("WiFiManager", context.getString(R.string.log_no_location_permission))
                return false
            }

            if (!wifiManager.isWifiEnabled) {
                android.util.Log.w("WiFiManager", context.getString(R.string.log_wifi_disabled))
                return false
            }

            val success = try { wifiManager.startScan() } catch (_: Throwable) { false }
            if (success) {
                android.util.Log.d("WiFiManager", context.getString(R.string.log_wifi_scan_started))
            } else {
                android.util.Log.w(
                    "WiFiManager",
                    "Error starting WiFi scan (possible throttling on Android 10+); using last results"
                )
            }
            success
        } catch (e: SecurityException) {
            android.util.Log.e("WiFiManager", "Security error while scanning WiFi: ${e.message}")
            false
        } catch (e: Exception) {
            android.util.Log.e("WiFiManager", "Unexpected error while scanning WiFi: ${e.message}")
            false
        }
    }

    /**
     * Obtiene información detallada de redes WiFi con escaneo automático
     */
    fun scanAndGetWiFiNetworks(): List<WiFiNetwork> {
        // Iniciar escaneo si es posible
        val scanStarted = startWifiScan()

        if (!scanStarted) {
            android.util.Log.w("WiFiManager", "No se pudo iniciar escaneo, usando resultados previos")
        }

        // Pequeña pausa para que el escaneo se complete (en un entorno real usaríamos callbacks)
        try {
            Thread.sleep(200) // 200ms para que el escaneo se complete parcialmente
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // Obtener resultados
        val networks = getWiFiNetworks()

        if (networks.isEmpty() && scanStarted) {
            android.util.Log.d("WiFiManager", "Escaneo completado pero no se encontraron redes")
        }

        return networks
    }

    fun getCellularNetworkInfo(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val networkOperator = telephonyManager.networkOperatorName ?: "Desconocido"
            @Suppress("DEPRECATION")
            val networkType = getNetworkTypeString(telephonyManager.networkType)
            val signalStrength = getSignalStrengthInfo()

            """
            📡 Red Celular Actual:
            • Operador: $networkOperator
            • Tipo de Red: $networkType
            • Intensidad de Señal: $signalStrength

            💡 Nota: Esta es tu red celular actual, no otras señales detectadas.
            """.trimIndent()
        } catch (e: Exception) {
            "📡 Cellular network information not available\n• Error: ${e.message}"
        }
    }

    fun getAvailableCellularNetworks(): List<String> {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val available = mutableSetOf<String>()

            // 1) SIMs activas
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    val subs = subscriptionManager.activeSubscriptionInfoList
                    if (!subs.isNullOrEmpty()) {
                        for (sub in subs) {
                            val name = sub.carrierName?.toString()
                            @Suppress("DEPRECATION") val mcc = sub.mcc
                            @Suppress("DEPRECATION") val mnc = sub.mnc
                            val plmn = "$mcc$mnc"
                            if (!name.isNullOrEmpty() && name != "Desconocido")
                                available.add("$name (SIM ${sub.simSlotIndex + 1})")
                            else
                                available.add("${getOperatorNameFromMccMnc(plmn)} (SIM ${sub.simSlotIndex + 1})")
                        }
                    }
                }
            } catch (_: SecurityException) { }

            // 2) Fallback a operador actual
            if (available.isEmpty()) {
                val currentOperator = telephonyManager.networkOperator
                val currentOperatorName = telephonyManager.networkOperatorName
                if (!currentOperatorName.isNullOrEmpty() && currentOperatorName != "Desconocido") {
                    available.add(currentOperatorName)
                } else if (!currentOperator.isNullOrEmpty() && currentOperator.length >= 5) {
                    available.add(getOperatorNameFromMccMnc(currentOperator))
                }
            }

            // 3a) Celdas visibles con TM por defecto
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    telephonyManager.allCellInfo?.forEach { cellInfo ->
                        when (cellInfo) {
                            is android.telephony.CellInfoGsm -> {
                                val plmn = mccMncFromGsm(cellInfo.cellIdentity)
                                if (plmn.isNotBlank()) {
                                    val op = getOperatorNameFromMccMnc(plmn)
                                    if (op != "Operador $plmn") available.add("$op (Disponible)")
                                }
                            }
                            is android.telephony.CellInfoLte -> {
                                val plmn = mccMncFromLte(cellInfo.cellIdentity)
                                if (plmn.isNotBlank()) {
                                    val op = getOperatorNameFromMccMnc(plmn)
                                    if (op != "Operador $plmn") available.add("$op (Disponible)")
                                }
                            }
                            is android.telephony.CellInfoWcdma -> {
                                val plmn = mccMncFromWcdma(cellInfo.cellIdentity)
                                if (plmn.isNotBlank()) {
                                    val op = getOperatorNameFromMccMnc(plmn)
                                    if (op != "Operador $plmn") available.add("$op (Disponible)")
                                }
                            }
                            is android.telephony.CellInfoTdscdma -> {
                                val plmn = mccMncFromTdscdma(cellInfo.cellIdentity)
                                if (plmn.isNotBlank()) {
                                    val op = getOperatorNameFromMccMnc(plmn)
                                    if (op != "Operador $plmn") available.add("$op (Disponible)")
                                }
                            }
                            is android.telephony.CellInfoCdma -> {
                                available.add("CDMA (Disponible)")
                            }
                            is android.telephony.CellInfoNr -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val ci = cellInfo.cellIdentity as? android.telephony.CellIdentityNr
                                    val mcc = runCatching { ci?.mccString }.getOrNull()
                                    val mnc = runCatching { ci?.mncString }.getOrNull()
                                    if (!mcc.isNullOrBlank() && !mnc.isNullOrBlank()) {
                                        val plmn = "$mcc$mnc"
                                        val op = getOperatorNameFromMccMnc(plmn)
                                        if (op != "Operador $plmn") available.add("$op (Disponible)")
                                    } else {
                                        available.add("5G NR (Disponible)")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: SecurityException) { }

            // 3b) Repetir por cada subId
            try {
                val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as android.telephony.SubscriptionManager
                val subs = runCatching { subscriptionManager.activeSubscriptionInfoList }.getOrNull()
                if (!subs.isNullOrEmpty()) {
                    for (sub in subs) {
                        val tmForSub = runCatching { telephonyManager.createForSubscriptionId(sub.subscriptionId) }.getOrNull() ?: continue
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            runCatching { tmForSub.allCellInfo }.getOrNull()?.forEach { cellInfo ->
                                when (cellInfo) {
                                    is android.telephony.CellInfoGsm -> {
                                        val plmn = mccMncFromGsm(cellInfo.cellIdentity)
                                        if (plmn.isNotBlank()) {
                                            val op = getOperatorNameFromMccMnc(plmn)
                                            if (op != "Operador $plmn") available.add("$op (Disponible)")
                                        }
                                    }
                                    is android.telephony.CellInfoLte -> {
                                        val plmn = mccMncFromLte(cellInfo.cellIdentity)
                                        if (plmn.isNotBlank()) {
                                            val op = getOperatorNameFromMccMnc(plmn)
                                            if (op != "Operador $plmn") available.add("$op (Disponible)")
                                        }
                                    }
                                    is android.telephony.CellInfoWcdma -> {
                                        val plmn = mccMncFromWcdma(cellInfo.cellIdentity)
                                        if (plmn.isNotBlank()) {
                                            val op = getOperatorNameFromMccMnc(plmn)
                                            if (op != "Operador $plmn") available.add("$op (Disponible)")
                                        }
                                    }
                                    is android.telephony.CellInfoTdscdma -> {
                                        val plmn = mccMncFromTdscdma(cellInfo.cellIdentity)
                                        if (plmn.isNotBlank()) {
                                            val op = getOperatorNameFromMccMnc(plmn)
                                            if (op != "Operador $plmn") available.add("$op (Disponible)")
                                        }
                                    }
                                    is android.telephony.CellInfoCdma -> {
                                        available.add("CDMA (Disponible)")
                                    }
                                    is android.telephony.CellInfoNr -> {
                                        val ci = cellInfo.cellIdentity as? android.telephony.CellIdentityNr
                                        val mcc = runCatching { ci?.mccString }.getOrNull()
                                        val mnc = runCatching { ci?.mncString }.getOrNull()
                                        if (!mcc.isNullOrBlank() && !mnc.isNullOrBlank()) {
                                            val plmn = "$mcc$mnc"
                                            val op = getOperatorNameFromMccMnc(plmn)
                                            if (op != "Operador $plmn") available.add("$op (Disponible)")
                                        } else {
                                            available.add("5G NR (Disponible)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) { }

            // 4) Complemento con hotspots Wi‑Fi de operador
            try {
                val carriersWifi = _carrierNetworks.value.mapNotNull { n ->
                    val ssid = n.ssid.lowercase()
                    when {
                        "movistar" in ssid -> "Movistar (Hotspot Wi‑Fi)"
                        "vodafone" in ssid -> "Vodafone España (Hotspot Wi‑Fi)"
                        "orange" in ssid -> "Orange España (Hotspot Wi‑Fi)"
                        "yoigo" in ssid -> "Yoigo (Hotspot Wi‑Fi)"
                        "digi" in ssid -> "DigiMobil (Hotspot Wi‑Fi)"
                        else -> null
                    }
                }
                available.addAll(carriersWifi)
            } catch (_: Exception) { }

            available.toList().sorted()

        } catch (e: SecurityException) {
            listOf("Insufficient permissions to scan networks")
        } catch (e: Exception) {
            listOf("Error scanning networks: ${e.message}")
        }
    }

    private fun getOperatorNameFromMccMnc(mccMnc: String): String {
        return when (mccMnc) {
            "21401" -> "Vodafone España"
            "21402", "21405", "21407", "21410", "21412", "21414", "21428", "21429", "21430" -> "Movistar"
            "21403", "21409", "21433" -> "Orange España"
            "21404", "21424", "21425" -> "Yoigo"
            "21406", "21411", "21413", "21431" -> "Vodafone España"
            "21408" -> "Euskaltel"
            "21421" -> "Jazztel"
            "21419" -> "Simyo"
            "21416" -> "Telecable"
            "21417" -> "Mundo-R"
            "21418" -> "ONO"
            "21420" -> "Fonyou"
            "21422" -> "DigiMobil"
            "21423" -> "Barablu"
            "21426" -> "Lycamobile"
            "21427" -> "Truphone"
            "21432" -> "Telefónica"
            "21434", "21435", "21436" -> "Aire Networks"
            "21415" -> "BT España"
            else -> "Operador $mccMnc"
        }
    }

    fun getCarrierNetworks(): List<String> {
        return getAvailableCellularNetworks()
    }

    fun getCarrierNetworkInfo(): String {
        val availableNetworks = getAvailableCellularNetworks()

        if (availableNetworks.isEmpty()) {
            return context.getString(R.string.wifi_no_mobile_networks_detected)
        }

        val networksText = availableNetworks.joinToString("\n• ") { "📶 $it" }
        val hasDiscovered = availableNetworks.any { it.contains("(Disponible)") }
        val onlySimDetected = availableNetworks.isNotEmpty() && !hasDiscovered

        val base = """
        ${context.getString(R.string.wifi_mobile_networks_detected_title)}
        • $networksText

        ${context.getString(R.string.wifi_mobile_networks_info_hint)}
        """.trimIndent()

        return if (onlySimDetected) {
            base + "\n\n${context.getString(R.string.wifi_mobile_networks_detection_limit)}"
        } else base
    }

    /**
     * Inicia la suscripción a cambios de señal/servicio/celdas para refrescar operador en tiempo real.
     */
    @Suppress("MissingPermission")
    fun startTelephonyUpdates() {
        if (isTelephonyRegistered) return
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: usar TelephonyCallback
                val executor = ContextCompat.getMainExecutor(context)
                val callback = object : android.telephony.TelephonyCallback(),
                    android.telephony.TelephonyCallback.SignalStrengthsListener,
                    android.telephony.TelephonyCallback.CellInfoListener,
                    android.telephony.TelephonyCallback.ServiceStateListener {
                    override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength) {
                        _carrierInfoFlow.value = safeCarrierInfo()
                    }
                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
                        _carrierInfoFlow.value = safeCarrierInfo()
                    }
                    override fun onServiceStateChanged(serviceState: ServiceState) {
                        _carrierInfoFlow.value = safeCarrierInfo()
                    }
                }
                tm.registerTelephonyCallback(executor, callback)
                telephonyCallbackApi31 = callback
                isTelephonyRegistered = true
                // Empuje inicial
                _carrierInfoFlow.value = safeCarrierInfo()
            } else {
                // APIs anteriores: PhoneStateListener
                val listener = object : PhoneStateListener() {
                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength?) {
                        super.onSignalStrengthsChanged(signalStrength)
                        _carrierInfoFlow.value = safeCarrierInfo()
                    }
                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onServiceStateChanged(serviceState: ServiceState?) {
                        super.onServiceStateChanged(serviceState)
                        _carrierInfoFlow.value = safeCarrierInfo()
                    }
                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
                        super.onCellInfoChanged(cellInfo)
                        _carrierInfoFlow.value = safeCarrierInfo()
                    }
                }
                phoneStateListener = listener
                @Suppress("DEPRECATION")
                tm.listen(
                    listener,
                    PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                        PhoneStateListener.LISTEN_CELL_INFO or
                        PhoneStateListener.LISTEN_SERVICE_STATE
                )
                isTelephonyRegistered = true
                _carrierInfoFlow.value = safeCarrierInfo()
            }
        } catch (_: SecurityException) {
            // Sin permisos suficientes, no suscribir.
        } catch (_: Exception) {
        }
    }

    /**
     * Detiene la suscripción a actualizaciones de telefonía.
     */
    fun stopTelephonyUpdates() {
        if (!isTelephonyRegistered) return
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = telephonyCallbackApi31 as? android.telephony.TelephonyCallback
                if (cb != null) {
                    tm.unregisterTelephonyCallback(cb)
                }
                telephonyCallbackApi31 = null
            } else {
                @Suppress("DEPRECATION")
                tm.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
                phoneStateListener = null
            }
        } catch (_: Exception) {
        } finally {
            isTelephonyRegistered = false
        }
    }

    private fun safeCarrierInfo(): String {
        return try { getCarrierNetworkInfo() } catch (_: Exception) { context.getString(R.string.wifi_signal_info_unavailable) }
    }

    /**
     * Obtiene intensidades de señal celular reales con frecuencia estimada para cálculos SAR.
     * Devuelve una lista de pares (dBm, frecuencia_MHz)
     */
    @Suppress("MissingPermission")
    fun getCellSignalStrengthsWithFreq(): List<Pair<Int, Double>> {
        val result = mutableListOf<Pair<Int, Double>>()
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val cellInfoList = telephonyManager.allCellInfo ?: return emptyList()
                for (cellInfo in cellInfoList) {
                    when (cellInfo) {
                        is android.telephony.CellInfoLte -> {
                            val ss = cellInfo.cellSignalStrength
                            val dbm = ss.rsrp.takeIf { it != android.telephony.CellInfo.UNAVAILABLE } ?: ss.dbm
                            val ci = cellInfo.cellIdentity
                            @Suppress("DEPRECATION")
                            val earfcn = if (Build.VERSION.SDK_INT >= 29) ci.earfcn else null
                            val freqMHz = lteEarfcnToHz(earfcn)?.div(1e6) ?: 1800.0
                            if (dbm != android.telephony.CellInfo.UNAVAILABLE) result.add(dbm to freqMHz)
                        }
                        is android.telephony.CellInfoNr -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val ss = cellInfo.cellSignalStrength as android.telephony.CellSignalStrengthNr
                                val srsrp = ss.ssRsrp
                                val dbm = if (srsrp != android.telephony.CellInfo.UNAVAILABLE) srsrp else ss.dbm
                                val ci = cellInfo.cellIdentity as android.telephony.CellIdentityNr
                                val nrarfcn = if (Build.VERSION.SDK_INT >= 29) ci.nrarfcn else null
                                val freqMHz = nrArfcnToHz(nrarfcn)?.div(1e6) ?: 3500.0
                                if (dbm != android.telephony.CellInfo.UNAVAILABLE) result.add(dbm to freqMHz)
                            }
                        }
                        is android.telephony.CellInfoWcdma -> {
                            val ss = cellInfo.cellSignalStrength
                            val dbm = ss.dbm
                            val ci = cellInfo.cellIdentity
                            val uarfcn = if (Build.VERSION.SDK_INT >= 30) ci.uarfcn else null
                            val freqMHz = uarfcnToHz(uarfcn)?.div(1e6) ?: 2100.0
                            if (dbm != android.telephony.CellInfo.UNAVAILABLE) result.add(dbm to freqMHz)
                        }
                        is android.telephony.CellInfoGsm -> {
                            val ss = cellInfo.cellSignalStrength
                            val dbm = ss.dbm
                            val ci = cellInfo.cellIdentity
                            val arfcn = if (Build.VERSION.SDK_INT >= 24) ci.arfcn else null
                            val freqMHz = gsmArfcnToHz(arfcn)?.div(1e6) ?: 900.0
                            if (dbm != android.telephony.CellInfo.UNAVAILABLE) result.add(dbm to freqMHz)
                        }
                        is android.telephony.CellInfoCdma -> {
                            val ss = cellInfo.cellSignalStrength
                            val dbm = ss.dbm
                            val freqMHz = 850.0
                            if (dbm != android.telephony.CellInfo.UNAVAILABLE) result.add(dbm to freqMHz)
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
        return result
    }

    // Conversores mínimos (duplicados simplificados de AmbientExposureManager para evitar dependencia cruzada)
    private fun lteEarfcnToHz(earfcn: Int?): Double? {
        if (earfcn == null || earfcn <= 0) return null
        return when (earfcn) {
            in 1200..1949 -> 1_800e6 // Band 3
            in 500..599 -> 850e6    // Band 5
            in 2750..3449 -> 2_600e6 // Band 7
            in 3450..3799 -> 700e6  // Band 28
            in 36200..36349 -> 2_300e6 // Band 40
            else -> null
        }
    }

    private fun nrArfcnToHz(nrarfcn: Int?): Double? {
        if (nrarfcn == null || nrarfcn <= 0) return null
        return when (nrarfcn) {
            in 0..599999 -> 3_500e6
            in 600000..2016666 -> 28_000e6
            else -> null
        }
    }

    private fun uarfcnToHz(uarfcn: Int?): Double? {
        if (uarfcn == null || uarfcn <= 0) return null
        return 2_100e6
    }

    private fun gsmArfcnToHz(arfcn: Int?): Double? {
        if (arfcn == null || arfcn <= 0) return null
        return when (arfcn) {
            in 0..124 -> 900e6
            in 512..885 -> 1_800e6
            else -> null
        }
    }

    // Helpers MCC/MNC con APIs modernas y retrocompatibilidad
    private fun mccMncFromGsm(id: android.telephony.CellIdentityGsm): String {
        return if (Build.VERSION.SDK_INT >= 29) {
            val mcc = id.mccString
            val mnc = id.mncString
            if (!mcc.isNullOrBlank() && !mnc.isNullOrBlank()) "$mcc$mnc" else ""
        } else {
            ""
        }
    }

    private fun mccMncFromLte(id: android.telephony.CellIdentityLte): String {
        return if (Build.VERSION.SDK_INT >= 29) {
            val mcc = id.mccString
            val mnc = id.mncString
            if (!mcc.isNullOrBlank() && !mnc.isNullOrBlank()) "$mcc$mnc" else ""
        } else {
            ""
        }
    }

    private fun mccMncFromWcdma(id: android.telephony.CellIdentityWcdma): String {
        return if (Build.VERSION.SDK_INT >= 30) {
            val mcc = id.mccString
            val mnc = id.mncString
            if (!mcc.isNullOrBlank() && !mnc.isNullOrBlank()) "$mcc$mnc" else ""
        } else {
            ""
        }
    }

    private fun mccMncFromTdscdma(id: android.telephony.CellIdentityTdscdma): String {
        return if (Build.VERSION.SDK_INT >= 29) {
            val mcc = id.mccString
            val mnc = id.mncString
            if (!mcc.isNullOrBlank() && !mnc.isNullOrBlank()) "$mcc$mnc" else ""
        } else {
            ""
        }
    }

    fun getDetailedCellInfo(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val networkOperator = telephonyManager.networkOperatorName ?: "Desconocido"
            @Suppress("DEPRECATION")
            val networkType = getNetworkTypeString(telephonyManager.networkType)
            val signalStrength = getSignalStrengthInfo()

            """
            📡 Información Detallada de Red Celular:
            • Operador: $networkOperator
            • Tipo de Red: $networkType
            • Intensidad de Señal: $signalStrength
            • Código de Operador: ${telephonyManager.networkOperator}

            💡 Esta información se actualiza en tiempo real
            """.trimIndent()
        } catch (e: Exception) {
            "📡 Error getting network information: ${e.message}"
        }
    }

    private fun getCellType(@Suppress("UNUSED_PARAMETER") cell: Any): String {
        // Sin NetMonster, usamos información básica
        return "Celular"
    }

    fun getSignalInfoList(): List<Any> {
        // Sin NetMonster, devolvemos lista vacía por ahora
        return emptyList()
    }

    private fun getNetworkTypeString(networkType: Int): String {
        return when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G (GPRS)"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
            TelephonyManager.NETWORK_TYPE_UMTS -> "3G (UMTS)"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G (HSDPA)"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G (HSUPA)"
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G (HSPA)"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G (LTE)"
            TelephonyManager.NETWORK_TYPE_NR -> "5G (NR)"
            else -> "Desconocido ($networkType)"
        }
    }

    private fun getSignalStrengthInfo(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Intentar obtener información de intensidad de señal de todas las celdas disponibles
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                val cellInfoList = telephonyManager.allCellInfo
                if (cellInfoList != null && cellInfoList.isNotEmpty()) {
                    val signalStrengths = mutableListOf<String>()

                    for (cellInfo in cellInfoList) {
                        when (cellInfo) {
                            is android.telephony.CellInfoGsm -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                val dbm = signalStrength.dbm
                                val level = signalStrength.level
                                val operator = getOperatorNameFromMccMnc(mccMncFromGsm(cellInfo.cellIdentity))
                                signalStrengths.add("$operator: ${dbm}dBm (${context.getString(R.string.wifi_signal_level)} $level)")
                            }
                            is android.telephony.CellInfoLte -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                val dbm = signalStrength.dbm
                                val level = signalStrength.level
                                val rsrp = signalStrength.rsrp // Reference Signal Received Power
                                val rsrq = signalStrength.rsrq // Reference Signal Received Quality
                                val operator = getOperatorNameFromMccMnc(mccMncFromLte(cellInfo.cellIdentity))
                                signalStrengths.add("$operator LTE: ${dbm}dBm (RSRP: ${rsrp}dBm, RSRQ: ${rsrq}dB, ${context.getString(R.string.wifi_signal_level)} $level)")
                            }
                            is android.telephony.CellInfoWcdma -> {
                                val signalStrength = cellInfo.cellSignalStrength
                                val dbm = signalStrength.dbm
                                val level = signalStrength.level
                                val operator = getOperatorNameFromMccMnc(mccMncFromWcdma(cellInfo.cellIdentity))
                                signalStrengths.add("$operator 3G: ${dbm}dBm (${context.getString(R.string.wifi_signal_level)} $level)")
                            }
                            is android.telephony.CellInfoNr -> {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    val signalStrength = cellInfo.cellSignalStrength
                                    val dbm = signalStrength.dbm
                                    val level = signalStrength.level
                                    signalStrengths.add("5G NR: ${dbm}dBm (${context.getString(R.string.wifi_signal_level)} $level)")
                                }
                            }
                        }
                    }

                    if (signalStrengths.isNotEmpty()) {
                        return signalStrengths.joinToString("\n")
                    }
                }
            }

            // Fallback: intentar obtener información básica de la señal actual
            val signalStrength = telephonyManager.signalStrength
            if (signalStrength != null) {
                return context.getString(R.string.wifi_current_signal) + " ${context.getString(R.string.wifi_basic_info_available)}\n${context.getString(R.string.wifi_check_location_permissions)}"
            }

            "📡 ${context.getString(R.string.wifi_signal_info_unavailable)}"
        } catch (e: SecurityException) {
            context.getString(R.string.wifi_insufficient_permissions)
        } catch (e: Exception) {
            context.getString(R.string.wifi_signal_error) + " ${e.message}"
        }
    }

    fun getBluetoothDevices(): List<BluetoothDevice> {
        // Requiere permiso de escaneo (para descubrimiento futuro) y especialmente CONNECT para acceder a bondedDevices, nombre y clase
        if (!hasBluetoothPermission() || !hasBluetoothConnectPermission()) {
            return emptyList()
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return emptyList()
        }

        return try {
            val devices = bluetoothAdapter.bondedDevices?.filter { device ->
                // Accesos protegidos por BLUETOOTH_CONNECT en Android 12+
                val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
                val devClass = runCatching { device.bluetoothClass?.deviceClass }.getOrNull()
                (major == 1024) || (devClass != null && audioDeviceClasses.contains(devClass))
            } ?: emptyList()

            _bluetoothDevices.value = devices
            devices
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            // getString(R.string.comment_handle_unexpected_error)
            emptyList()
        }
    }

    fun isWifiEnabled(): Boolean {
        return wifiManager.isWifiEnabled
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun getExposureReadings(): List<ExposureReading> {
        // Limitar a las redes más potentes para evitar inundar lecturas (top 10 por nivel)
        val networks = getWiFiNetworks()
            .sortedBy { it.level } // level en dBm negativo, más bajo = más fuerte
            .take(10)
        val bluetoothDevices = getBluetoothDevices()

        val readings = mutableListOf<ExposureReading>()

        // Calcular exposición WiFi regular
        networks.forEach { network ->
            val wifiLevel = Math.abs(network.level).toDouble() / 100.0
            val isCarrierNetwork = carrierSSIDs.any { carrierSSID ->
                network.ssid.contains(carrierSSID, ignoreCase = true)
            }

            // Redes de compañías telefónicas tienen mayor exposición
            val carrierMultiplier = if (isCarrierNetwork) 1.5 else 1.0
            val adjustedWifiLevel = wifiLevel * carrierMultiplier

            readings.add(ExposureReading(
                timestamp = System.currentTimeMillis(),
                wifiLevel = adjustedWifiLevel,
                // Importante: no duplicar conteo. El SAR de WiFi se maneja aparte en Home como conversión.
                sarLevel = 0.0,
                bluetoothLevel = 0.0,
                type = ExposureType.WIFI,
                source = network.ssid
            ))
        }

        // Evitar añadir entradas Bluetooth con valor 0.0; serán calculadas por SARManager cuando proceda
        // Mantener esta sección sin generar lecturas de bluetooth hasta tener estimaciones reales

    // Calculate total exposure de las lecturas generadas aquí (WiFi)
    val totalExposureValue = readings.sumOf { it.wifiLevel + it.sarLevel + it.bluetoothLevel }
        _totalExposure.value = totalExposureValue

        return readings
    }

    // Eliminadas funciones de SAR sintético (WiFi/Bluetooth). Mantener valores reales de sensores.

    /**
     * Obtiene estadísticas específicas de exposición
     */
    fun getExposureStats(): Map<String, Any> {
        val networks = getWiFiNetworks()
        val carriers = _carrierNetworks.value
        val bluetoothDevices = getBluetoothDevices()

        return mapOf(
            "totalNetworks" to networks.size,
            "carrierNetworks" to carriers.size,
            "bluetoothDevices" to bluetoothDevices.size,
            "highPowerNetworks" to networks.count { Math.abs(it.level) > 70 },
            "wifiEnabled" to isWifiEnabled(),
            "bluetoothEnabled" to isBluetoothEnabled()
        )
    }
}
