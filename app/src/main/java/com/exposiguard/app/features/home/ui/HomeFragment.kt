package com.exposiguard.app.features.home.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import com.exposiguard.app.databinding.FragmentHomeBinding
import com.exposiguard.app.viewmodel.GeneralViewModel
import com.exposiguard.app.managers.HealthManager
import com.exposiguard.app.managers.NoiseManager
import com.exposiguard.app.managers.SARManager
import com.exposiguard.app.managers.TrendsAnalysisManager
import com.exposiguard.app.managers.UserProfileManager
import com.exposiguard.app.managers.WiFiManager
import com.exposiguard.app.managers.BluetoothManager
import com.exposiguard.app.managers.AmbientExposureManager
import com.exposiguard.app.managers.PhysicalSensorManager
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import androidx.navigation.fragment.findNavController
import com.exposiguard.app.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GeneralViewModel by viewModels()

    @Inject lateinit var wiFiManager: WiFiManager
    @Inject lateinit var bluetoothManager: BluetoothManager
    @Inject lateinit var ambientExposureManager: AmbientExposureManager
    @Inject lateinit var physicalSensorManager: PhysicalSensorManager
    @Inject lateinit var healthManager: HealthManager
    @Inject lateinit var sarManager: SARManager
    @Inject lateinit var noiseManager: NoiseManager
    @Inject lateinit var trendsManager: TrendsAnalysisManager
    @Inject lateinit var userProfileManager: UserProfileManager
    @Inject lateinit var exposureRepository: ExposureRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // getString(R.string.comment_initialize_managers_deferred)
        initializeManagersAsync()

        // Verificar si es un nuevo día y resetear si es necesario
        checkAndResetDailyData()

        // Setup UI básico primero
        setupBasicUI()
    // Configurar semáforo inicial
    setupTrafficLight()

        // Botón para ver detalle en EMF (si existe en layout)
        try {
            val emfBtn = binding.root.findViewById<com.google.android.material.button.MaterialButton>(com.exposiguard.app.R.id.btn_view_emf_details)
            emfBtn?.setOnClickListener {
                try { findNavController().navigate(com.exposiguard.app.R.id.nav_emf) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Cargar datos iniciales de forma asíncrona
        loadInitialDataAsync()
    }

    private fun initializeManagersAsync() {
        // getString(R.string.comment_use_handler_initialize)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            try {
                // Verificar que el fragmento sigue adjunto antes de proceder
                if (!isAdded || context == null) {
                    return@Thread
                }

                // Los managers ya están inyectados por Hilt, no necesitamos crearlos manualmente

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
    // Configurar UI básica
    binding.text24hExposure.text = getString(R.string.home_calculating_exposure)
    }

    private fun loadInitialDataAsync() {
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

            // Home minimalista: solo total 24h
            binding.text24hExposure.text = calculate24HourExposure()
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Error in setupHomeContent", e)
            binding.text24hExposure.text = "${getString(R.string.home_error_setup_content)} ${e.message}"
        }
    }

    private fun loadHomeData() {
        // Aquí se podrían cargar datos en tiempo real si fuera necesario
        // Por ahora, la información estática es suficiente
    }

    private fun calculate24HourExposure(): String {
        val readings = exposureRepository.getAllReadings()
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

        // Si no hay lecturas del día actual, devolvemos un mensaje simple y actualizamos semáforo a seguro
        if (todayReadings.isEmpty()) {
            updateTrafficLight(0.0, getRecommendedDailyLimit())
            return "${getString(R.string.home_exposure_day)} -- W/kg (0%) • ${getString(R.string.home_no_measurements)}"
        }

        // Calcular exposición total promedio ponderada por tiempo
        // Usar el máximo valor en lugar de la suma para evitar valores excesivamente altos
        val maxWifiLevel = todayReadings.map { it.wifiLevel }.maxOrNull() ?: 0.0
        val maxSarLevel = todayReadings.map { it.sarLevel }.maxOrNull() ?: 0.0
        val totalExposure = maxWifiLevel + maxSarLevel

        // Obtener límite recomendado según estándar y perfil
        val recommendedLimit = getRecommendedDailyLimit()

        // Limitar el valor máximo para evitar lecturas irrealmente altas
        val clampedExposure = totalExposure.coerceAtMost(recommendedLimit * 2)

        val percentage = (clampedExposure / recommendedLimit * 100).coerceIn(0.0, 200.0)

        // Determinar estado y color
        // status textual ya no se usa en la UI simplificada; mantenemos solo el semáforo visual

        // Actualizar semáforo con el valor clamped
        updateTrafficLight(clampedExposure, recommendedLimit)

    // Devolver solo lo importante: total del día y porcentaje
    return "${getString(R.string.home_exposure_day)} ${String.format(java.util.Locale.getDefault(), "%.3f", clampedExposure)} W/kg (${String.format(java.util.Locale.getDefault(), "%.1f", percentage)}% de ${String.format(java.util.Locale.getDefault(), "%.2f", recommendedLimit)} W/kg) • ${todayReadings.size} mediciones"
    }

    private fun getRecommendedDailyLimit(): Double {
        // Obtener el estándar seleccionado desde SharedPreferences
        val sharedPrefs = requireContext().getSharedPreferences("exposiguard_prefs", android.content.Context.MODE_PRIVATE)
        val standard = sharedPrefs.getString("exposure_standard", "FCC") ?: "FCC"

    // En la Home simplificada no se usa talla para el límite; valores legales absolutos

        // Límite base según estándar (valores diarios recomendados)
        val baseLimit = when (standard) {
            "ICNIRP" -> 2.0 // W/kg para ICNIRP
            "FCC" -> 1.6 // W/kg para FCC
            else -> 1.6
        }

    // No escalar por talla en Home simplificada: límite legal absoluto por estándar
    return baseLimit
    }

    private fun setupTrafficLight() {
        // Configurar estado inicial del semáforo
        updateTrafficLight(0.0, getRecommendedDailyLimit()) // Valores por defecto
    }

    private fun updateTrafficLight(currentExposure: Double, limit: Double) {
        val percentage = (currentExposure / limit * 100).coerceIn(0.0, 200.0)

        // Resetear todas las luces
        binding.lightRed.alpha = 0.3f
        binding.lightYellow.alpha = 0.3f
        binding.lightGreen.alpha = 0.3f

        // Encender la luz correspondiente
        when {
            percentage >= 100 -> {
                // Rojo: exposición crítica
                binding.lightRed.alpha = 1.0f
                binding.textTrafficLightStatus.text = getString(R.string.home_critical)
                binding.textTrafficLightMessage.text = getString(R.string.home_critical_message)
            }
            percentage >= 80 -> {
                // Amarillo: exposición alta
                binding.lightYellow.alpha = 1.0f
                binding.textTrafficLightStatus.text = getString(R.string.home_high)
                binding.textTrafficLightMessage.text = getString(R.string.home_high_message)
            }
            else -> {
                // Verde: exposición segura
                binding.lightGreen.alpha = 1.0f
                binding.textTrafficLightStatus.text = getString(R.string.home_safe)
                binding.textTrafficLightMessage.text = getString(R.string.home_safe_message)
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
        // Aquí podríamos implementar lógica para limpiar datos antiguos
        // Por ahora, solo guardamos la fecha del reseteo
        // En una implementación más completa, podríamos:
        // 1. Archivar datos del día anterior
        // 2. Limpiar lecturas antiguas
        // 3. Resetear contadores diarios

        val sharedPrefs = requireContext().getSharedPreferences("exposiguard_prefs", android.content.Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        // Resetear contadores diarios si existen
        editor.putInt("daily_reading_count", 0)
        editor.putFloat("daily_exposure_total", 0.0f)
        editor.putLong("daily_start_time", System.currentTimeMillis())

        editor.apply()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
