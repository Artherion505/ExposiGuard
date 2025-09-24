package com.exposiguard.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.bluetooth.BluetoothDevice
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.exposiguard.app.R
import com.exposiguard.app.data.WiFiNetwork
import com.exposiguard.app.databinding.FragmentWifiBinding
import com.exposiguard.app.managers.EMFManager
import com.exposiguard.app.managers.WiFiManager
import com.exposiguard.app.managers.BluetoothManager
import com.exposiguard.app.repository.ExposureRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlin.math.pow

private const val WIFI_MAX_DISPLAY = 20

@AndroidEntryPoint
class WiFiFragment : Fragment() {

    private lateinit var binding: FragmentWifiBinding
    @Inject lateinit var wiFiManager: WiFiManager
    @Inject lateinit var emfManager: EMFManager
    @Inject lateinit var exposureRepository: ExposureRepository
    @Inject lateinit var bluetoothManager: BluetoothManager

    // UI update throttling system
    private val uiUpdateHandler = Handler(Looper.getMainLooper())
    private var lastWifiUpdate = 0L
    private var lastBluetoothUpdate = 0L
    private var lastCarrierUpdate = 0L
    private val UPDATE_THROTTLE_MS = 500L // 500ms entre actualizaciones para mejor responsividad
    private var btCollectorStarted = false

    // Limpieza periódica de UI (solo borra el texto en pantalla, no los datos persistidos)
    private val uiClearHandler = Handler(Looper.getMainLooper())
    private val CLEAR_UI_INTERVAL_MS = 120_000L // cada 2 minutos
    private val uiClearRunnable = object : Runnable {
        override fun run() {
            if (!isAdded || view == null) return
            // Limpiar únicamente la salida visual; las lecturas ya se guardaron en el repositorio
            runCatching {
                binding.textEmf.text = ""
                // Mantener un estado breve para indicar que el panel se limpió
                binding.textWifiStatus.text = getString(R.string.wifi_ready)
            }
            // Reprogramar
            uiClearHandler.postDelayed(this, CLEAR_UI_INTERVAL_MS)
        }
    }

    // Job de escaneo periódico respetando Settings
    private var periodicScanJob: Job? = null
    // Último listado de redes para filtrar
    private var lastNetworks: List<WiFiNetwork> = emptyList()

    // Receiver para resultados de escaneo Wi‑Fi
    private var wifiReceiverRegistered = false
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                val networks = kotlin.runCatching { wiFiManager.getWiFiNetworks() }.getOrDefault(emptyList())
                throttledWifiUpdate(networks)
                updateStatusLine(statusHint = getString(R.string.wifi_results_updated))
            }
        }
    }

    // Activity Result API para permisos en tiempo de ejecución
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val anyGranted = results.values.any { it }
        if (anyGranted) startWifiScanning() else binding.textWifiStatus.text = getString(R.string.wifi_permission_denied)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWifiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure runtime permissions
        ensureRuntimePermissions()

        // Initialize basic UI
        setupBasicUI()

        // Initialize managers asynchronously
        initializeManagersAsync()

        // Programar limpieza periódica del panel visual
        uiClearHandler.removeCallbacks(uiClearRunnable)
        uiClearHandler.postDelayed(uiClearRunnable, CLEAR_UI_INTERVAL_MS)

        // Arrancar escaneo periódico según Settings
        startPeriodicScan()

        // Actualizaciones de telefonía en tiempo real deshabilitadas (experimental removido)
        runCatching {
            wiFiManager.stopTelephonyUpdates()
            wiFiManager.carrierInfoFlow
                .onEach { info -> if (!info.isNullOrBlank()) binding.textTelephony.text = info }
                .launchIn(viewLifecycleOwner.lifecycleScope)
        }

        // Reaccionar a cambios de Settings (intervalo)
        com.exposiguard.app.utils.AppEvents.events
            .onEach { ev ->
                if (ev is com.exposiguard.app.utils.AppEvents.Event.SettingsChanged) {
                    restartPeriodicScan()
                    // Experimental: sin reaplicar telefonía en tiempo real (removido)
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun ensureRuntimePermissions() {
        val needed = mutableListOf<String>()
        // Ubicación (necesaria antes de Android 13, y en muchos OEMs incluso con Nearby)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        // Android 13+: permiso específico Nearby Wi‑Fi Devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.NEARBY_WIFI_DEVICES
            }
        }

        // Bluetooth (Android 12+ requiere permisos separados)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.BLUETOOTH_SCAN
            }
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }
        }

        // Telefonía (para nombre de operador y celdas en algunas APIs)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.READ_PHONE_STATE
        }

        if (needed.isNotEmpty()) {
            binding.textWifiStatus.text = getString(R.string.wifi_location_permission_required)
            binding.textWifiStatus.setTextColor(resources.getColor(R.color.error_color, null))
            // Solicitar permisos con Activity Result API
            permissionLauncher.launch(needed.toTypedArray())
            // Snackbar para ir a ajustes de la app si el usuario negó previamente
            Snackbar.make(binding.root, R.string.wifi_permissions_error_full, Snackbar.LENGTH_LONG)
                .setAction(R.string.permissions_settings) {
                    runCatching {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.parse("package:" + requireContext().packageName)
                        })
                    }
                }
                .show()
            return
        }

        // Hints de sistema: Wi‑Fi/Ubicación apagados
        if (!wiFiManager.isWifiEnabled()) {
            binding.textWifiStatus.text = getString(R.string.wifi_disabled_hint)
            Snackbar.make(binding.root, R.string.wifi_disabled_hint, Snackbar.LENGTH_LONG)
                .setAction(R.string.wifi_enable_action) {
                    runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                }
                .show()
            return
        }
        if (!wiFiManager.isLocationEnabled()) {
            binding.textWifiStatus.text = getString(R.string.wifi_location_disabled)
            Snackbar.make(binding.root, R.string.wifi_location_disabled, Snackbar.LENGTH_LONG)
                .setAction(R.string.wifi_enable_action) {
                    runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }
                .show()
        }

        startWifiScanning()
    }

    private fun setupBasicUI() {
        // Initialize status text
        binding.textWifiStatus.text = getString(R.string.wifi_initializing)

        // Setup refresh button
        binding.btnRetryScan.setOnClickListener { startWifiScanning() }

        // Permitir abrir ajustes tocando el estado
        binding.textWifiStatus.setOnClickListener {
            when {
                !wiFiManager.isWifiEnabled() -> {
                    // Abrir ajustes de Wi‑Fi
                    runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                }
                !wiFiManager.isLocationEnabled() -> {
                    // Abrir ajustes de Ubicación
                    runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }
                else -> startWifiScanning()
            }
        }
    }

    private fun initializeManagersAsync() {
        lifecycleScope.launch {
            try {
                // Initialize WiFi manager
                if (::wiFiManager.isInitialized) {
                    binding.textWifiStatus.text = getString(R.string.wifi_ready)
                    startWifiScanning()
                } else {
                    binding.textWifiStatus.text = getString(R.string.wifi_manager_not_initialized)
                }
            } catch (e: Exception) {
                binding.textWifiStatus.text = getString(R.string.wifi_initialization_error)
            }
        }
    }

    private fun startWifiScanning() {
        try {
            if (!wiFiManager.isWifiEnabled()) {
                binding.textWifiStatus.text = getString(R.string.wifi_disabled_hint)
                return
            }

            if (!wiFiManager.hasLocationPermission() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED)) {
                binding.textWifiStatus.text = getString(R.string.wifi_permissions_error_full)
                return
            }

            binding.textWifiStatus.text = getString(R.string.wifi_scanning)

            // Register WiFi receiver
            if (!wifiReceiverRegistered) {
                requireContext().registerReceiver(
                    wifiScanReceiver,
                    IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                )
                wifiReceiverRegistered = true
            }

            // Start WiFi scan
            val success = wiFiManager.startWifiScan()
            if (!success) {
                binding.textWifiStatus.text = getString(R.string.wifi_scan_failed)
            }

            // También refrescar de inmediato la lista visible con los últimos resultados
            val networks = kotlin.runCatching { wiFiManager.getWiFiNetworks() }.getOrDefault(emptyList())
            throttledWifiUpdate(networks)

            // Actualizar secciones de Telefonía y Bluetooth
            throttledCarrierUpdate()
            startBluetoothScanAndUpdate()
        } catch (e: Exception) {
            binding.textWifiStatus.text = getString(R.string.wifi_scan_error)
        }
    }

    private fun throttledWifiUpdate(networks: List<WiFiNetwork>) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastWifiUpdate >= UPDATE_THROTTLE_MS) {
            lastWifiUpdate = currentTime
            updateWifiDisplay(networks)
        }
    }

    private fun updateWifiDisplay(networks: List<WiFiNetwork>) {
        lifecycleScope.launch {
            try {
                // Cachear para filtrado
                lastNetworks = networks

                val emfInfo = StringBuilder()

                // WiFi: mostrar potencia recibida total aproximada en mW (desde dBm)
                if (networks.isNotEmpty()) {
                    val totalMilliwatts = networks.sumOf { dBmToMilliwatts(it.level) }
                    emfInfo.append(getString(R.string.wifi_emf_wifi_source))
                    emfInfo.append(" ${String.format(java.util.Locale.getDefault(), "%.6f", totalMilliwatts.coerceAtLeast(0.0))} mW\n")
                }

                // Bluetooth: potencia estimada total en mW por dispositivo emparejado relevante
                val bluetoothDevices = wiFiManager.getBluetoothDevices()
                if (bluetoothDevices.isNotEmpty()) {
                    val totalBtMw = bluetoothDevices.sumOf { estimateBluetoothMilliwatts(it) }
                    emfInfo.append(getString(R.string.wifi_emf_bluetooth_source))
                    emfInfo.append(" ${String.format(java.util.Locale.getDefault(), "%.6f", totalBtMw.coerceAtLeast(0.0))} mW\n")
                }

                // Celular: sumar mW a partir del dBm de celdas detectadas
                val cellStrengths = wiFiManager.getCellSignalStrengthsWithFreq()
                if (cellStrengths.isNotEmpty()) {
                    val totalCellMw = cellStrengths.sumOf { (dbm, _) -> dBmToMilliwatts(dbm) }
                    emfInfo.append(getString(R.string.wifi_emf_cellular_source))
                    emfInfo.append(" ${String.format(java.util.Locale.getDefault(), "%.12f", totalCellMw.coerceAtLeast(0.0))} mW\n")
                }

                // Update EMF display
                if (emfInfo.isNotEmpty()) {
                    binding.textEmf.text = emfInfo.toString()
                } else {
                    binding.textEmf.text = getString(R.string.wifi_no_emf_detected)
                }

                // Update status con conteo
                val count = networks.size
                binding.textWifiStatus.text = if (count > 0) {
                    getString(R.string.wifi_results_updated) + " ($count)"
                } else {
                    getString(R.string.wifi_scan_completed)
                }

                // Pintar listado de redes (Top WIFI_MAX_DISPLAY, orden por nivel)
                val sorted = networks.sortedBy { it.level }.take(WIFI_MAX_DISPLAY)
                val text = if (sorted.isEmpty()) {
                    getString(R.string.wifi_no_networks_detected)
                } else {
                    buildString {
                        sorted.forEach { n ->
                            append("• ")
                            append(if (n.ssid.isNotBlank()) n.ssid else "(sin SSID)")
                            append("  ")
                            append(n.level)
                            append(" dBm  ")
                            append(n.frequency)
                            append(" MHz  ch ")
                            append(freqToChannel(n.frequency))
                            append("\n")
                        }
                    }
                }
                binding.textWifi.text = text

                // Configurar autocompletado de filtro una vez
                setupFilterAutocomplete()

            } catch (e: Exception) {
                binding.textWifiStatus.text = getString(R.string.wifi_update_error)
            }
        }
    }

    private fun updateStatusLine(statusHint: String) {
        binding.textWifiStatus.text = statusHint
    }

    private fun throttledCarrierUpdate() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastCarrierUpdate >= UPDATE_THROTTLE_MS) {
            lastCarrierUpdate = currentTime
            updateCarrierDisplay()
        }
    }

    private fun startBluetoothScanAndUpdate() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBluetoothUpdate >= UPDATE_THROTTLE_MS) {
            lastBluetoothUpdate = currentTime
            // Iniciar escaneo si es posible
            if (bluetoothManager.isBluetoothEnabled()) {
                bluetoothManager.startBluetoothScan()
            }
            // Recoger dispositivos emparejados y/o descubiertos
            if (!btCollectorStarted) {
                bluetoothManager.bluetoothDevices
                    .onEach { devices -> updateBluetoothDisplay(devices) }
                    .launchIn(viewLifecycleOwner.lifecycleScope)
                btCollectorStarted = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Unregister receiver
        if (wifiReceiverRegistered) {
            try {
                requireContext().unregisterReceiver(wifiScanReceiver)
            } catch (e: Exception) {
                // Receiver might already be unregistered
            }
            wifiReceiverRegistered = false
        }

        // Cancelar limpieza periódica de UI
        uiClearHandler.removeCallbacks(uiClearRunnable)

        // Cancelar escaneo periódico
        periodicScanJob?.cancel()

        // Detener escaneo BT si estuviera activo
        runCatching { bluetoothManager.stopBluetoothScan() }

        // Detener actualizaciones de telefonía
        runCatching { wiFiManager.stopTelephonyUpdates() }
    }

    // companion object removido: REQUEST_LOCATION_PERMISSION ya no es necesario

    // --- Helpers UI ---
    private fun setupFilterAutocomplete() {
        try {
            val ssids = lastNetworks.map { it.ssid }.filter { it.isNotBlank() }.distinct().sorted()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ssids)
            binding.wifiFilterInput.setAdapter(adapter)
            binding.wifiFilterInput.setOnItemClickListener { _, _, pos, _ ->
                val selected = adapter.getItem(pos)
                applyFilter(selected)
            }
        } catch (_: Exception) {}
    }

    private fun applyFilter(query: String?) {
        val q = query?.trim()?.lowercase().orEmpty()
        val filtered = if (q.isBlank()) lastNetworks else lastNetworks.filter { it.ssid.lowercase().contains(q) }
        val sorted = filtered.sortedBy { it.level }.take(WIFI_MAX_DISPLAY)
        val text = if (sorted.isEmpty()) {
            getString(R.string.wifi_no_networks_detected)
        } else {
            buildString {
                sorted.forEach { n ->
                    append("• ")
                    append(if (n.ssid.isNotBlank()) n.ssid else "(sin SSID)")
                    append("  ")
                    append(n.level)
                    append(" dBm  ")
                    append(n.frequency)
                    append(" MHz  ch ")
                    append(freqToChannel(n.frequency))
                    append("\n")
                }
            }
        }
        binding.textWifi.text = text
    }

    private fun startPeriodicScan() {
        periodicScanJob?.cancel()
        periodicScanJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                startWifiScanning()
                val intervalMillis = com.exposiguard.app.utils.AppEvents.getMonitoringIntervalMillis(requireContext())
                delay(intervalMillis)
            }
        }
    }

    private fun restartPeriodicScan() {
        periodicScanJob?.cancel()
        startPeriodicScan()
    }

    // Mapear frecuencia (MHz) a canal Wi‑Fi (2.4/5/6 GHz)
    private fun freqToChannel(freqMHz: Int): String {
        return try {
            when {
                // 2.4 GHz
                freqMHz == 2484 -> "14"
                freqMHz in 2412..2472 -> {
                    val ch = 1 + (freqMHz - 2412) / 5
                    ch.toString()
                }
                // 5 GHz: fórmula general
                freqMHz in 4915..5825 -> {
                    val ch = (freqMHz - 5000) / 5
                    ch.toString()
                }
                // 6 GHz (Wi‑Fi 6E): canal 1 a 5955 MHz; aproximación común
                freqMHz in 5925..7125 -> {
                    val ch = (freqMHz - 5950) / 5
                    ch.toString()
                }
                else -> "-"
            }
        } catch (_: Exception) { "-" }
    }

    private fun dBmToMilliwatts(dBm: Int): Double {
        // mW = 10^(dBm/10)
        return 10.0.pow(dBm / 10.0)
    }

    private fun estimateBluetoothMilliwatts(device: BluetoothDevice): Double {
    val devClass = runCatching { device.bluetoothClass?.deviceClass }.getOrNull() ?: -1
    val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull() ?: -1
        val baseDbm = when {
            // Audio específicos
            devClass in listOf(1024, 1028, 1032, 1044, 1048) -> -20 // auriculares ~0.01 mW
            major == 1024 -> -20 // AUDIO_VIDEO
            // Wearables
            devClass == 1076 || major == 7936 -> -25 // reloj ~0.003 mW
            // Periféricos
            major == 1280 -> -30 // teclado/ratón ~0.001 mW
            // Altavoz BT
            runCatching { device.name?.contains("speaker", true) }.getOrNull() == true -> -10 // ~0.1 mW
            else -> -15 // default ~0.03 mW
        }
        return 10.0.pow(baseDbm / 10.0)
    }

    private fun updateCarrierDisplay() {
        try {
            val info = wiFiManager.getCarrierNetworkInfo()
            binding.textTelephony.text = info
        } catch (_: Exception) {
            binding.textTelephony.text = getString(R.string.wifi_signal_info_unavailable)
        }
    }

    private fun deviceTypeLabel(device: BluetoothDevice): String {
        val devClass = runCatching { device.bluetoothClass?.deviceClass }.getOrNull() ?: -1
        val major = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull() ?: -1
        return when {
            // AUDIO/VIDEO
            devClass in listOf(1024, 1028, 1032, 1044, 1048) || major == 1024 -> getString(R.string.wifi_device_type_headphones)
            // PHONE
            major == 512 -> getString(R.string.wifi_device_type_phone)
            // COMPUTER
            major == 256 -> getString(R.string.wifi_device_type_computer)
            // PERIPHERAL (teclado/ratón)
            major == 1280 -> getString(R.string.wifi_device_type_generic)
            else -> getString(R.string.wifi_device_type_generic)
        }
    }

    private fun updateBluetoothDisplay(devices: List<BluetoothDevice>) {
        val text = if (devices.isEmpty()) {
            getString(R.string.wifi_bluetooth_placeholder)
        } else {
            buildString {
                append("📶 Dispositivos detectados: ")
                append(devices.size)
                append('\n')
                devices.forEach { d ->
                    append("• ")
                    append(d.name ?: getString(R.string.wifi_bluetooth_device))
                    append("  (")
                    append(deviceTypeLabel(d))
                    append(")\n")
                }
            }
        }
        binding.textBluetooth.text = text
    }
}
