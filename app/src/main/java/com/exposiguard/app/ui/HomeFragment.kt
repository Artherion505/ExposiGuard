package com.exposiguard.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.fragment.app.viewModels
import com.exposiguard.app.databinding.FragmentHomeBinding
import com.exposiguard.app.viewmodel.GeneralViewModel
import com.exposiguard.app.managers.HealthManager
import com.exposiguard.app.managers.NoiseManager
import com.exposiguard.app.managers.SARManager
import com.exposiguard.app.managers.TrendsAnalysisManager
import com.exposiguard.app.managers.UserProfileManager
import com.exposiguard.app.managers.WiFiManager
import com.exposiguard.app.managers.BluetoothManager
import com.exposiguard.app.managers.AppUsageManager
import com.exposiguard.app.managers.AmbientExposureManager
import com.exposiguard.app.managers.PhysicalSensorManager
import com.exposiguard.app.R
import com.google.android.material.card.MaterialCardView
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.exposiguard.app.repository.ExposureRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import androidx.navigation.fragment.findNavController
import javax.inject.Inject
import android.webkit.WebView
import android.webkit.WebViewClient

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GeneralViewModel by viewModels()
    @Inject lateinit var wiFiManager: WiFiManager
    @Inject lateinit var sarManager: SARManager
    @Inject lateinit var bluetoothManager: BluetoothManager
    @Inject lateinit var ambientManager: AmbientExposureManager
    @Inject lateinit var physicalSensorManager: PhysicalSensorManager
    @Inject lateinit var healthManager: HealthManager
    @Inject lateinit var noiseManager: NoiseManager
    @Inject lateinit var trendsManager: TrendsAnalysisManager
    @Inject lateinit var exposureRepository: ExposureRepository
    @Inject lateinit var userProfileManager: UserProfileManager
    @Inject lateinit var appUsageManager: AppUsageManager
    private var samplingJob: Job? = null
    private var sarSamplingJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    // getString(R.string.comment_placeholder_avoid_reference_error)
    private fun updateAmbientCard() {
        try {
            val prefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
            val includeAmbient = prefs.getBoolean("include_ambient_antennas", false)
            if (!includeAmbient) return
            // En futuras iteraciones, renderizar resumen de AmbientExposureManager aquí.
        } catch (_: Exception) { }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ${getString(R.string.comment_initialize_managers_deferred)}
        initializeManagersAsync()

        // Verificar si es un nuevo día y resetear si es necesario
        checkAndResetDailyData()

        // Configurar semáforo de exposición
        setupTrafficLight()

        // Setup UI básico primero
        setupBasicUI()

        // Botón 'Ver detalle EMF'
        try {
            binding.btnViewEmfDetails.setOnClickListener {
                try { findNavController().navigate(com.exposiguard.app.R.id.nav_emf) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Ko-fi support button
        try {
            val kofiHtml = """
                <html>
                <head>
                    <style>
                        body { margin: 0; padding: 0; display: flex; justify-content: center; align-items: center; height: 100vh; }
                        #kofi-widget { width: 96px; height: 96px; }
                    </style>
                </head>
                <body>
                    <div id="kofi-widget"></div>
                    <script type='text/javascript' src='https://storage.ko-fi.com/cdn/widget/Widget_2.js'></script>
                    <script type='text/javascript'>
                        kofiwidget2.init('Support me on Ko-fi', '#72a4f2', 'Q5Q61JMR4M');
                        kofiwidget2.draw();
                    </script>
                </body>
                </html>
            """.trimIndent()

            binding.btnKofiSupport.settings.javaScriptEnabled = true
            binding.btnKofiSupport.webViewClient = WebViewClient()
            binding.btnKofiSupport.loadData(kofiHtml, "text/html", "UTF-8")
        } catch (_: Exception) {}

        // Cargar datos iniciales de forma asíncrona
        loadInitialDataAsync()
        updateAmbientCard()
    }

    private fun initializeManagersAsync() {
        // ${getString(R.string.comment_use_handler_initialize)}
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            try {
                // Verificar que el fragmento sigue adjunto antes de proceder
                if (!isAdded || context == null) {
                    return@Thread
                }

                // Los managers ya están inyectados por Hilt

                // Crear ViewModel con dependencias en el hilo principal
                handler.post {
                    // Verificar nuevamente que el fragmento sigue adjunto
                    if (!isAdded || _binding == null) {
                        return@post
                    }

                    try {
                        // El ViewModel ya está inyectado por Hilt
                        // Una vez inicializado el ViewModel, configurar la UI completa
                        setupHomeContent()
                        updateAmbientCard()
                    } catch (e: Exception) {
                        android.util.Log.e("HomeFragment", "Error creating ViewModel", e)
                        binding.text24hExposure.text = "${getString(R.string.home_error_viewmodel)} ${e.message}"
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Error initializing managers", e)
                handler.post {
                    // Verificar que el fragmento sigue adjunto antes de actualizar UI
                    if (isAdded && _binding != null) {
                        binding.text24hExposure.text = "${getString(R.string.home_error_initializing)} ${e.message}"
                    }
                }
            }
        }.start()
    }

    private fun setupBasicUI() {
        // Configurar UI básica mientras se inicializan los managers
        binding.text24hExposure.text = getString(R.string.home_calculating_exposure)
        // Mostrar progress bar para cálculo inicial
        binding.progressInitialCalculation.visibility = View.VISIBLE
    }    private fun loadInitialDataAsync() {
        // Cargar datos básicos de forma asíncrona
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Aquí se podrían cargar datos en tiempo real si fuera necesario
            // Por ahora, la información estática es suficiente
        }, 100)
    }

    private fun setupHomeContent() {
        try {
            // Verificar que el ViewModel y managers están inicializados
            if (!::wiFiManager.isInitialized) {
                binding.text24hExposure.text = getString(R.string.home_managers_not_initialized)
                return
            }

            // Ingestar lecturas iniciales y arrancar muestreo periódico
            startExposureSampling()
            startLiveSarSampling()
            // Pintar primera vez
            binding.text24hExposure.text = calculate24HourExposure()

            // Ocultar progress bar una vez terminado el cálculo inicial
            binding.progressInitialCalculation.visibility = View.GONE

            // Suscribirse a eventos de la app para recalcular
            com.exposiguard.app.utils.AppEvents.events
                .onEach {
                    when (it) {
                        is com.exposiguard.app.utils.AppEvents.Event.DataChanged,
                        is com.exposiguard.app.utils.AppEvents.Event.SettingsChanged -> {
                            binding.text24hExposure.text = calculate24HourExposure()
                            // Snackbar corto indicando que se actualizó
                            try { com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.home_updated), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() } catch (_: Exception) {}
                        }
                    }
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error in setupHomeContent", e)
            binding.text24hExposure.text = "${getString(R.string.home_error_setup_content)} ${e.message}"
        }
    }

    private fun setupTrafficLight() {
        // Configurar estado inicial del semáforo
        updateTrafficLight(0.0, 1.6) // Valores por defecto
    }

    private fun updateTrafficLight(currentExposure: Double, limit: Double) {
        val percentage = (currentExposure / limit * 100).coerceIn(0.0, 200.0)

        // Actualizar chip de estado
        val (statusText, recommendationText) = when {
            percentage >= 100 -> {
                getString(R.string.home_critical) to getString(R.string.home_critical_message)
            }
            percentage >= 80 -> {
                getString(R.string.home_high) to getString(R.string.home_high_message)
            }
            else -> {
                getString(R.string.home_safe) to getString(R.string.home_safe_message)
            }
        }

        binding.chipExposureStatus.text = statusText
        binding.textRecommendation.text = recommendationText

        // Actualizar progress bar de exposición
        val progressValue = percentage.toInt().coerceIn(0, 100)
        try {
            binding.progressExposureCircular.max = 100
            binding.progressExposureCircular.progress = progressValue
            binding.textExposurePercent.text = String.format(java.util.Locale.getDefault(), "%.1f%%", percentage)
        } catch (_: Exception) { /* vistas pueden no existir en variantes antiguas */ }

        // Actualizar semáforo visible
        try {
            val ctx = requireContext()
            val green = androidx.core.content.ContextCompat.getColor(ctx, com.exposiguard.app.R.color.traffic_green)
            val yellow = androidx.core.content.ContextCompat.getColor(ctx, com.exposiguard.app.R.color.traffic_yellow)
            val red = androidx.core.content.ContextCompat.getColor(ctx, com.exposiguard.app.R.color.traffic_red)
            val off = androidx.core.content.ContextCompat.getColor(ctx, com.exposiguard.app.R.color.traffic_off)

            fun tint(view: android.widget.ImageView, color: Int) {
                view.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            }

            // Reset all to off
            tint(binding.lightRedVisible, off)
            tint(binding.lightYellowVisible, off)
            tint(binding.lightGreenVisible, off)

            when {
                percentage >= 100 -> tint(binding.lightRedVisible, red)
                percentage >= 80 -> tint(binding.lightYellowVisible, yellow)
                else -> tint(binding.lightGreenVisible, green)
            }
        } catch (_: Exception) { }
    }

    private fun calculate24HourExposure(): String {
        // Usar lecturas recientes (24h) en lugar de todas
        val readings = exposureRepository.getRecentReadings(hours = 24)
        val currentTime = System.currentTimeMillis()

        // Calcular el inicio del día actual (00:00:00 del día actual)
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = currentTime
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        // Filtrar lecturas del día actual
    val todayReadings = readings.filter { it.timestamp >= startOfDay }

        if (todayReadings.isEmpty()) {
            // No inventar datos: mostrar mensaje claro
            updateTrafficLight(0.0, getRecommendedDailyLimit())
            return getString(R.string.home_daily_exposure) + " -- W/kg (0%) • 0 " + getString(R.string.home_measurements)
        }

    // Promedio ponderado por tiempo (TWA) sobre el día actual
    // Atar el límite de huecos (maxGapMs) al intervalo configurado en Settings (2–3×)
    val settingsPrefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
    val monitorIntervalMinutes = settingsPrefs.getString("monitoring_interval", "5")?.toLongOrNull() ?: 5L
        val capMultiplier = 3L // usar 3× por defecto para ser tolerante a pequeños saltos
    val maxGapMs = (monitorIntervalMinutes * capMultiplier).coerceAtLeast(1L) * 60_000L
        val (twaWifi, twaSar, twaBt) = exposureRepository.timeWeightedAverageComponents(startOfDay, currentTime, maxGapMs)
        // TWA mano (estimación): dose (J/kg) / segundos transcurridos del día
        val secondsElapsed = ((currentTime - startOfDay) / 1000.0).coerceAtLeast(1.0)
        val handDoseJPerKg = try { if (::bluetoothManager.isInitialized) bluetoothManager.handAbsorptionToday.value else 0.0 } catch (_: Exception) { 0.0 }
        val twaHand = (handDoseJPerKg / secondsElapsed).coerceAtLeast(0.0)
        val wifiAsSarApprox = (twaWifi.coerceAtLeast(0.0)) * 0.2
        // Exposición comparable al límite legal (W/kg): por defecto, solo SAR+BT+mano (promedio del día)
        var sarLikeExposureDaily = (twaSar + twaBt + twaHand)
        // Toggle experimental: incluir WiFi normalizado en el porcentaje si el usuario lo habilita
        // Preferencia experimental eliminada; no se incluye WiFi normalizado salvo fallback interno
        val includeWifiPref = false
        // Fallback: si SAR+BT+mano≈0 pero hay WiFi>0, incluir WiFi normalizado para un feedback visible
        val includeWifiRuntimeDaily = includeWifiPref || (sarLikeExposureDaily <= 0.0 && twaWifi > 0.0)
        if (includeWifiRuntimeDaily) {
            sarLikeExposureDaily += wifiAsSarApprox
        }

        // Aporte opcional: índice ambiental (proveído por usuario)
        // Preferir el toggle de la pestaña Ambiente RF si existe; fallback al ajuste experimental previo
        val prefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
    val includeAmbientToggle = prefs.getBoolean("ambient_rf_include_in_home", false)
        var ambientIndex = 0
        var ambientS_mWm2: Double? = null
        var ambientSarLike = 0.0
        if (includeAmbientToggle) {
            val estimate = com.exposiguard.app.utils.AmbientRfEstimator.estimateFromPrefs(requireContext())
            ambientIndex = estimate.index
            ambientS_mWm2 = estimate.sTotalWPerM2 * 1000.0
            ambientSarLike = estimate.sarLikeWPerKg
            sarLikeExposureDaily += ambientSarLike
        }

        // Obtener límite recomendado según estándar y perfil
        val recommendedLimit = getRecommendedDailyLimit()
        val dailyPercentage = (sarLikeExposureDaily / recommendedLimit * 100).coerceIn(0.0, 200.0)

    // "Ahora" y semáforo: usar promedio ponderado por tiempo en ventana derivada de Settings
    val windowMs = com.exposiguard.app.utils.AppEvents.getAveragingWindowMillis(requireContext())
    val startWindow = (currentTime - windowMs).coerceAtLeast(startOfDay)
    val maxGapWindow = (monitorIntervalMinutes * 3L).coerceAtLeast(1L) * 60_000L
    val (winWifi, winSar, winBt) = exposureRepository.timeWeightedAverageComponents(startWindow, currentTime, maxGapWindow)
    val winWifiAsSar = (winWifi.coerceAtLeast(0.0)) * 0.2
    var nowSarLike = (winSar + winBt)
    // Usar mismo fallback para la ventana instantánea
    val includeWifiRuntimeWindow = includeWifiRuntimeDaily || (nowSarLike <= 0.0 && winWifi > 0.0)
    if (includeWifiRuntimeWindow) nowSarLike += winWifiAsSar
    nowSarLike += ambientSarLike
    val instantTotal = nowSarLike.coerceAtLeast(0.0)
    val instantPercentage = (instantTotal / recommendedLimit * 100).coerceIn(0.0, 200.0)

    // Actualizar semáforo usando el promedio en ventana
    updateTrafficLight(instantTotal, recommendedLimit)

    // Asegurar que el porcentaje visual refleje progreso del día o del instante (el mayor)
    try {
        // Mostrar estrictamente el porcentaje instantáneo para coincidir con la pestaña EMF
        val progressValue = instantPercentage.toInt().coerceIn(0, 100)
        binding.progressExposureCircular.max = 100
        binding.progressExposureCircular.progress = progressValue
        binding.textExposurePercent.text = String.format(java.util.Locale.getDefault(), "%.1f%%", instantPercentage)
    } catch (_: Exception) { /* vistas pueden no existir en variantes antiguas */ }

    // Chip de depuración opcional: mostrar n de muestras en ventana, ventana y maxGap
    try {
        val dbgPrefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
        val isDebuggable = try {
            (requireContext().applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) { false }
        val showDebug = false
        val nInWindow = exposureRepository.getReadingsInTimeRange(startWindow, currentTime).size
        val gapMin = (maxGapWindow / 60_000L).toInt()
        val winMin = (windowMs / 60_000L).toInt()
        // Chip de depuración eliminado
    } catch (_: Exception) {}

        // Calcular tiempo transcurrido del día
    // Tiempo transcurrido no mostrado en Home minimalista

    // Índice EMF y desglose removidos en la Home simplificada

    // Devolver solo lo importante: total del día y porcentaje
        val ambientSuffix = if (includeAmbientToggle) {
            val s = ambientS_mWm2?.let { " • S≈" + String.format(java.util.Locale.getDefault(), "%.1f", it) + " mW/m²" } ?: ""
            " • Ambient: ${ambientIndex}/100" + s
        } else ""

        // Mostrar ambos: promedio del día y porcentaje instantáneo
        val dailyPart = getString(R.string.home_daily_exposure) + " " + String.format(java.util.Locale.getDefault(), "%.3f", sarLikeExposureDaily) +
            " W/kg (" + String.format(java.util.Locale.getDefault(), "%.1f", dailyPercentage) + "% de " + String.format(java.util.Locale.getDefault(), "%.2f", recommendedLimit) + " W/kg)"
        val instantPart = " • Ahora (promedio " + (windowMs / 60_000L) + "m): " + String.format(java.util.Locale.getDefault(), "%.1f", instantPercentage) + "%"
        val backfillHint = try {
            val dayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(currentTime))
            val used = requireContext().getSharedPreferences("exposure_data", android.content.Context.MODE_PRIVATE)
                .getBoolean("initial_backfill_done_" + dayKey, false)
            if (used) " " + getString(com.exposiguard.app.R.string.home_backfill_active) else ""
        } catch (_: Exception) { "" }
        return dailyPart + " • ${todayReadings.size} " + getString(R.string.home_measurements) + instantPart + ambientSuffix + backfillHint
    }

    private fun getRecommendedDailyLimit(): Double {
        // Obtener el estándar seleccionado desde SharedPreferences
        // Intentar leer desde settings (UserProfileManager) y fallback a prefs antiguas
        val settingsPrefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
        val rawStandard = settingsPrefs.getString("exposure_standard", null)
            ?: requireContext().getSharedPreferences("exposiguard_prefs", android.content.Context.MODE_PRIVATE)
                .getString("exposure_standard", "FCC")
            ?: "FCC"
        val standard = when {
            rawStandard.contains("ICNIRP", ignoreCase = true) -> "ICNIRP"
            rawStandard.contains("FCC", ignoreCase = true) -> "FCC"
            else -> "FCC"
        }

        // Límite base según estándar (valores diarios recomendados)
    val baseLimit = when (standard) {
            "ICNIRP" -> 2.0 // W/kg para ICNIRP
            "FCC" -> 1.6 // W/kg para FCC
            else -> 1.6
        }
    // No escalar por talla: los límites legales son absolutos por estándar
    return baseLimit
    }

    private fun startExposureSampling() {
        // Cancelar si ya hay un job
        samplingJob?.cancel()
        samplingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                try {
                    val newReadings = wiFiManager.getExposureReadings()
                    if (newReadings.isNotEmpty()) {
                        exposureRepository.addExposureReadings(newReadings)
                        // Actualizar UI tras nueva ingesta
                        val content = calculate24HourExposure()
                        binding.text24hExposure.text = content
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeFragment", "Error sampling exposure", e)
                }
                // Muestreo según intervalo configurado
                val intervalMillis = com.exposiguard.app.utils.AppEvents.getMonitoringIntervalMillis(requireContext())
                delay(intervalMillis)
            }
        }
    }

    private fun startLiveSarSampling() {
        sarSamplingJob?.cancel()
        sarSamplingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                try {
                    val sar = sarManager.getCurrentSARLevel()
                    val br = try { sarManager.getCurrentSARBreakdown() } catch (_: Exception) { null }
                    // Persistir lectura SAR aislada para el día
                    val reading = com.exposiguard.app.data.ExposureReading(
                        timestamp = System.currentTimeMillis(),
                        wifiLevel = 0.0,
                        sarLevel = sar,
                        bluetoothLevel = 0.0,
                        type = com.exposiguard.app.data.ExposureType.SAR,
                        source = "Live SAR"
                    )
                    exposureRepository.addExposureReading(reading)
                    // Actualizar UI
                    val base = calculate24HourExposure()
                    val extra = if (br != null) "\nDesglose SAR (última muestra):\n• Torres: ${String.format(java.util.Locale.getDefault(), "%.3f", br.towers)} W/kg\n• Uso del teléfono: ${String.format(java.util.Locale.getDefault(), "%.3f", br.phoneUse)} W/kg\n• Total: ${String.format(java.util.Locale.getDefault(), "%.3f", br.total)} W/kg" else ""
                    binding.text24hExposure.text = base + "\n" + extra
                } catch (e: Exception) {
                    android.util.Log.e("HomeFragment", "Error sampling live SAR", e)
                }
                // Muestreo SAR en vivo según intervalo configurado
                val intervalMillis = com.exposiguard.app.utils.AppEvents.getMonitoringIntervalMillis(requireContext())
                delay(intervalMillis)
            }
        }
    }

    private fun createProgressBar(percentage: Double): String {
        val barLength = 20
        val filledLength = (percentage / 100 * barLength).toInt().coerceIn(0, barLength)

        val filled = "█".repeat(filledLength)
        val empty = "░".repeat(barLength - filledLength)

    return "[$filled$empty] ${String.format(java.util.Locale.getDefault(), "%.1f", percentage)}%"
    }

    private fun checkAndResetDailyData() {
        val sharedPrefs = requireContext().getSharedPreferences("exposiguard_prefs", android.content.Context.MODE_PRIVATE)
        val lastResetDate = sharedPrefs.getLong("last_reset_date", 0)
        val currentDate = getCurrentDateInMillis()

        // Si es un día diferente al último reseteo, limpiar datos antiguos
        if (currentDate > lastResetDate) {
            resetDailyData()
            // Guardar la nueva fecha de reseteo
            sharedPrefs.edit().putLong("last_reset_date", currentDate).apply()
        }
    }

    private fun getCurrentDateInMillis(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun resetDailyData() {
        // 1) Calcular y archivar totales del día que termina
        try {
            val now = System.currentTimeMillis()
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = now
            // Fin de ayer
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            val end = start + 24L * 60 * 60 * 1000

            // Evitar doble guardado si recién instalamos
            if (!::exposureRepository.isInitialized) exposureRepository = ExposureRepository(requireContext())
            val (twaWifi, twaSar, twaBt) = exposureRepository.timeWeightedAverageComponents(start, end, maxGapMs = null)
            val prefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
            val includeWifi = prefs.getBoolean("include_wifi_normalized", false)
            val wifiNorm = (twaWifi * 0.2).coerceAtLeast(0.0)
            val sarLike = (twaSar + twaBt + if (includeWifi) wifiNorm else 0.0).coerceAtLeast(0.0)

            val repo = com.exposiguard.app.repository.MonthlyStatsRepository(requireContext())
            val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val dateStr = df.format(java.util.Date(start))
            repo.saveDailyTotal(
                com.exposiguard.app.repository.DailyTotal(
                    date = dateStr,
                    sarLike = sarLike,
                    wifiNorm = wifiNorm,
                    sar = twaSar,
                    bt = twaBt
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("HomeFragment", "No se pudo archivar total diario: ${e.message}")
        }

        // 2) Resetear contadores simples
        val sharedPrefs = requireContext().getSharedPreferences("exposiguard_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putInt("daily_reading_count", 0)
            .putFloat("daily_exposure_total", 0.0f)
            .putLong("daily_start_time", System.currentTimeMillis())
            .apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    samplingJob?.cancel()
    sarSamplingJob?.cancel()
        _binding = null
    }
}
