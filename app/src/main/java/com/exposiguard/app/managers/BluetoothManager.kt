package com.exposiguard.app.managers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.app.AppOpsManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManager @Inject constructor(
    private val context: Context,
    private val appUsageManager: AppUsageManager,
    private val userProfileManager: UserProfileManager
) {

    private val _bluetoothDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val bluetoothDevices: StateFlow<List<BluetoothDevice>> = _bluetoothDevices.asStateFlow()

    private val _bluetoothExposure = MutableStateFlow(0.0)
    val bluetoothExposure: StateFlow<Double> = _bluetoothExposure.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _handAbsorptionToday = MutableStateFlow(0.0)
    val handAbsorptionToday: StateFlow<Double> = _handAbsorptionToday.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothManager.adapter
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val currentDevices = _bluetoothDevices.value.toMutableList()
                        if (!currentDevices.contains(it)) {
                            currentDevices.add(it)
                            _bluetoothDevices.value = currentDevices
                            calculateBluetoothExposure(currentDevices)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    init {
        registerReceiver()
    // Calcular absorción de mano al iniciar
    updateHandAbsorption()
    }

    fun startBluetoothScan() {
        bluetoothAdapter?.let { adapter ->
            if (adapter.isEnabled && !_isScanning.value) {
                try {
                    _isScanning.value = true
                    adapter.startDiscovery()
                } catch (_: SecurityException) {
                    _isScanning.value = false
                }
            }
        }
    }

    /**
     * Estima la absorción electromagnética en la mano por uso del teléfono hoy.
     * Modelo simple: dose[J/kg] = SAR_eff[W/kg] * tiempo[s], con factor_mano.
     * SAR_eff aproximado a 0.5 W/kg cuando el teléfono está en uso en mano.
     * factor_mano ~ 0.4 (fracción de energía absorbida localizada en mano).
     */
    fun updateHandAbsorption() {
        try {
            val hasAccess = appUsageManager.hasUsageAccess()
            if (!hasAccess) {
                _handAbsorptionToday.value = 0.0
                return
            }
            val timeMs = appUsageManager.getTodayTotalForegroundTimeMs()
            val timeSec = (timeMs / 1000.0).coerceAtLeast(0.0)

            // Ajuste por perfil (IMC puede afectar absorción total; aquí mantenemos simple)
            val weight = userProfileManager.getWeight().coerceAtLeast(50.0)
            val height = userProfileManager.getHeight().coerceAtLeast(150.0)
            val heightM = height / 100.0
            val bmi = weight / (heightM * heightM)
            val bmiFactor = when {
                bmi < 18.5 -> 0.9
                bmi < 25.0 -> 1.0
                bmi < 30.0 -> 1.1
                else -> 1.2
            }

            val sarEff = 0.5 // W/kg estimado durante uso en mano
            val handFactor = 0.4 // fracción localizada en mano
            val doseJPerKg = sarEff * timeSec * handFactor * bmiFactor

            _handAbsorptionToday.value = doseJPerKg
        } catch (_: Exception) {
            _handAbsorptionToday.value = 0.0
        }
    }

    fun openUsageAccessSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun hasUsageAccess(): Boolean = runCatching { appUsageManager.hasUsageAccess() }.getOrDefault(false)

    fun stopBluetoothScan() {
        bluetoothAdapter?.let { adapter ->
            if (_isScanning.value) {
                try {
                    adapter.cancelDiscovery()
                } catch (_: SecurityException) {
                    // ignorar
                } finally {
                    _isScanning.value = false
                }
            }
        }
    }

    private fun calculateBluetoothExposure(devices: List<BluetoothDevice>): Double {
        // Cálculo simplificado de exposición Bluetooth
        // En un caso real, esto sería más complejo considerando distancia, potencia, etc.
        val baseExposure = 0.001 // W/kg base por dispositivo
        val exposure = devices.size * baseExposure
        _bluetoothExposure.value = exposure
        return exposure
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver ya desregistrado
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun getPairedDevices(): Set<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices ?: emptySet()
        } catch (_: SecurityException) {
            emptySet()
        }
    }
}
