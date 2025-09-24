package com.exposiguard.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.exposiguard.app.R
import com.exposiguard.app.databinding.FragmentEmfBinding
import com.exposiguard.app.managers.EMFManager
import com.exposiguard.app.managers.SARManager
import com.exposiguard.app.managers.WiFiManager
import com.exposiguard.app.managers.BluetoothManager
import com.exposiguard.app.managers.AmbientExposureManager
import com.exposiguard.app.managers.PhysicalSensorManager
import com.exposiguard.app.managers.HealthManager
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.repository.MonthlyStatsRepository
import com.google.android.material.snackbar.Snackbar
import com.exposiguard.app.utils.AmbientRfEstimator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class EMFFragment : Fragment() {

    private var _binding: FragmentEmfBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var emfManager: EMFManager
    @Inject lateinit var sarManager: SARManager
    @Inject lateinit var wiFiManager: WiFiManager
    @Inject lateinit var bluetoothManager: BluetoothManager
    @Inject lateinit var ambientExposureManager: AmbientExposureManager
    @Inject lateinit var physicalSensorManager: PhysicalSensorManager
    @Inject lateinit var healthManager: HealthManager
    @Inject lateinit var exposureRepository: ExposureRepository
    @Inject lateinit var monthlyStatsRepository: MonthlyStatsRepository

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeManagers()
        setupUI()
        startMonitoring()
    }

    private fun initializeManagers() {
        // Los managers ya están inyectados por Hilt
    }

    private fun setupUI() {
        // Configurar límite SAR dinámico
        updateLimitDisplay()
        setupBarChart()
    }

    private fun updateLimitDisplay() {
        val prefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
        val standard = prefs.getString("exposure_standard", "FCC (USA)") ?: "FCC (USA)"
        val limit = emfManager.calculateExposureLimits(standard)
        binding.textLimit.text = getString(R.string.sar_limit_value_format, limit)
    }

    private fun setupBarChart() {
        lifecycleScope.launch {
            try {
                val monthTotals = monthlyStatsRepository.getMonthTotals()
                if (monthTotals.isNotEmpty()) {
                    val values = monthTotals.map { it.sarLike }
                    val max = (values.maxOrNull() ?: 2.0)
                    val labels = monthTotals.map { it.date.substring(8, 10) + "/" + it.date.substring(5, 7) }

                    binding.barChart.setData(values, labels, max)
                    binding.barChart.onBarSelected = { idx ->
                        monthTotals.getOrNull(idx)?.let { item ->
                            showBarDetailDialog(item)
                        }
                    }
                }
            } catch (e: Exception) {
                // Manejar errores silenciosamente
            }
        }
    }

    private fun startMonitoring() {
        lifecycleScope.launch {
            while (isActive) {
                try {
                    updateEMFData()
                    delay(2000) // Actualizar cada 2 segundos
                } catch (e: Exception) {
                    // Manejar errores silenciosamente para no interrumpir el monitoreo
                }
            }
        }
    }

    private fun updateEMFData() {
        try {
            val prefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
            val standard = prefs.getString("exposure_standard", "FCC (USA)") ?: "FCC (USA)"
            val limit = emfManager.calculateExposureLimits(standard)

            // Usar el mismo enfoque de Home: promedio ponderado por tiempo en ventana (TWA)
            val now = System.currentTimeMillis()
            val windowMs = com.exposiguard.app.utils.AppEvents.getAveragingWindowMillis(requireContext())
            val startWindow = now - windowMs
            val monitorIntervalMinutes = prefs.getString("monitoring_interval", "5")?.toLongOrNull() ?: 5L
            val maxGapWindow = (monitorIntervalMinutes * 3L).coerceAtLeast(1L) * 60_000L
            val (winWifi, winSar, winBt) = exposureRepository.timeWeightedAverageComponents(startWindow, now, maxGapWindow)

            // Calcular fallback diario como en Home
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = now
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startOfDay = cal.timeInMillis
            val (twaWifiDay, twaSarDay, twaBtDay) = exposureRepository.timeWeightedAverageComponents(startOfDay, now, (monitorIntervalMinutes * 3L).coerceAtLeast(1L) * 60_000L)
            val wifiAsSarApproxDay = (twaWifiDay.coerceAtLeast(0.0)) * 0.2
            var sarLikeDaily = (twaSarDay + twaBtDay)
            val includeWifiRuntimeDaily = (sarLikeDaily <= 0.0 && twaWifiDay > 0.0)
            if (includeWifiRuntimeDaily) sarLikeDaily += wifiAsSarApproxDay

            // Fallback de WiFi normalizado si SAR+BT ~0 pero hay WiFi > 0
            val winWifiAsSar = (winWifi.coerceAtLeast(0.0)) * 0.2
            var nowSarLike = (winSar + winBt)
            val includeWifiRuntimeWindow = includeWifiRuntimeDaily || (nowSarLike <= 0.0 && winWifi > 0.0)
            if (includeWifiRuntimeWindow) nowSarLike += winWifiAsSar

            // Ambiente RF: incluir el sarLike total (además, conservar chips de desglose)
            val includeAmbient = prefs.getBoolean("ambient_rf_include_in_home", false)
            val ambientEstimate = if (includeAmbient) AmbientRfEstimator.estimateFromPrefs(requireContext()) else null
            val ambientSarLike = ambientEstimate?.sarLikeWPerKg ?: 0.0
            val broadcastSar = ambientEstimate?.sarBroadcastWPerKg ?: 0.0
            nowSarLike += ambientSarLike

            // Calcular porcentaje coherente con Home
            val pct = (nowSarLike / limit * 100).coerceIn(0.0, 200.0)

            // Actualizar UI en el hilo principal
            activity?.runOnUiThread {
                // Obtener desglose actual para mostrar componentes
                val sarBreakdown = sarManager.getCurrentSARBreakdown()
                binding.textCurrent.text = getString(R.string.emf_total_estimated_format, nowSarLike, pct)
                binding.textBreakdown.text = if (broadcastSar > 0.0) {
                    getString(
                        R.string.emf_breakdown_with_broadcast_format,
                        sarBreakdown.towers,
                        sarBreakdown.phoneUse,
                        broadcastSar,
                        sarBreakdown.wifi,
                        sarBreakdown.bluetooth
                    )
                } else {
                    getString(
                        R.string.emf_breakdown_format,
                        sarBreakdown.towers,
                        sarBreakdown.phoneUse,
                        sarBreakdown.wifi,
                        sarBreakdown.bluetooth
                    )
                }

                // Chips de S (mW/m²) por componente ambiental si el toggle está activo
                binding.breakdownChips.removeAllViews()
                if (includeAmbient && ambientEstimate != null) {
                    fun addChip(label: String, valueMwPerM2: Double) {
                        if (valueMwPerM2 <= 0.0) return
                        val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                            isCheckable = false
                            isClickable = false
                            isFocusable = false
                            isCloseIconVisible = false
                            text = "$label S≈" + String.format(Locale.getDefault(), "%.2f mW/m²", valueMwPerM2)
                        }
                        binding.breakdownChips.addView(chip)
                    }

                    // Mostrar por componente y total ambiental
                    val sFmMw = ambientEstimate.sFmWPerM2 * 1000.0
                    val sTvMw = ambientEstimate.sTvWPerM2 * 1000.0
                    val sAmMw = ambientEstimate.sAmWPerM2 * 1000.0
                    val sTotalMw = ambientEstimate.sTotalWPerM2 * 1000.0

                    addChip(getString(R.string.emf_component_broadcast) + " FM: ", sFmMw)
                    addChip(getString(R.string.emf_component_broadcast) + " TV: ", sTvMw)
                    addChip(getString(R.string.emf_component_broadcast) + " AM: ", sAmMw)
                    addChip("Ambient Total: ", sTotalMw)
                } else {
                    // Si no hay ambiente, mantener el grupo vacío o mostrar nada
                }

                // Actualizar progreso
                val rawProgress = pct.toInt().coerceIn(0, 100)
                val displayProgress = when {
                    nowSarLike == 0.0 -> 0
                    rawProgress == 0 && nowSarLike > 0.0 -> 3
                    rawProgress in 1..4 -> 5
                    else -> rawProgress
                }
                binding.progress.progress = displayProgress

                // Actualizar recomendaciones
                updateRecommendations(nowSarLike, limit)
            }
        } catch (e: Exception) {
            activity?.runOnUiThread {
                binding.textCurrent.text = getString(R.string.emf_error, e.message)
            }
        }
    }

    private fun updateRecommendations(sarValue: Double, limit: Double) {
        val ratio = sarValue / limit
        val recommendations = when {
            ratio < 0.1 -> listOf(
                getString(R.string.emf_rec_low_1),
                getString(R.string.emf_rec_low_2)
            )
            ratio < 0.5 -> listOf(
                getString(R.string.emf_rec_moderate_1),
                getString(R.string.emf_rec_moderate_2),
                getString(R.string.emf_rec_moderate_3)
            )
            ratio < 1.0 -> listOf(
                getString(R.string.emf_rec_high_1),
                getString(R.string.emf_rec_high_2),
                getString(R.string.emf_rec_high_3)
            )
            else -> listOf(
                getString(R.string.emf_rec_critical_1),
                getString(R.string.emf_rec_critical_2),
                getString(R.string.emf_rec_critical_3)
            )
        }

        binding.textRecommendations.text = getString(R.string.emf_recommendations_prefix) + recommendations.joinToString("\n- ")
    }

    private fun showBarDetailDialog(item: Any) {
        // Esta función necesitaría ser implementada según el tipo de datos de MonthlyStatsRepository
        // Por ahora, mostrar un mensaje simple
        Snackbar.make(binding.root, getString(R.string.home_updated), Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
