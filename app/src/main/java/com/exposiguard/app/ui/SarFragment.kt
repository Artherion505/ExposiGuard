package com.exposiguard.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.exposiguard.app.databinding.FragmentSarBinding
import com.exposiguard.app.managers.SARManager
import com.exposiguard.app.managers.EMFManager
import com.exposiguard.app.managers.UserProfileManager
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

@AndroidEntryPoint
class SarFragment : Fragment() {

    private var _binding: FragmentSarBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var sarManager: SARManager

    private lateinit var userProfileManager: UserProfileManager
    private val emfManager = EMFManager()
    private val exposureRepository: ExposureRepository by lazy { ExposureRepository(requireContext()) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userProfileManager = UserProfileManager(requireContext())
        setupUI()
        observeData()

        // Refrescar al cambiar settings/datos
        com.exposiguard.app.utils.AppEvents.events
            .onEach {
                when (it) {
                    is com.exposiguard.app.utils.AppEvents.Event.SettingsChanged,
                    is com.exposiguard.app.utils.AppEvents.Event.DataChanged -> {
                        calculateManualSar()
                        updateHistorySummary()
                        try { com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.sar_updated), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() } catch (_: Exception) {}
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupUI() {
        binding.apply {
            // Texto informativo
            profileInfoText.text = getString(R.string.sar_profile_info)

            // Body parts para factor de absorción relativo local
            val bodyParts = listOf(
                getString(R.string.sar_body_part_head),
                getString(R.string.sar_body_part_body),
                getString(R.string.sar_body_part_hand)
            )
            bodyPartSpinner.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, bodyParts)
            bodyPartSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    updateBodyPartFactorText()
                    calculateManualSar()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

            // Sliders de señal y distancia
            signalStrengthSlider.addOnChangeListener { _, value, _ ->
                signalStrengthValueText.text = getString(R.string.sar_signal_value_format, value)
            }
            distanceSlider.addOnChangeListener { _, value, _ ->
                distanceValueText.text = getString(R.string.sar_distance_value_format, value)
            }

            // Mostrar estándar desde Settings
            val savedStandard = userProfileManager.getExposureStandard()
            standardValueText.text = savedStandard
            updateStandardLimitText()

            // Botón calcular
            calculateButton.setOnClickListener { calculateManualSar() }

            // Selectores de rango histórico
            rangeToggle.check(range5h.id)
            val rangeListener = View.OnClickListener {
                updateHistorySummary()
            }
            range5h.setOnClickListener(rangeListener)
            range24h.setOnClickListener(rangeListener)
            range30d.setOnClickListener(rangeListener)

            // Cálculo inicial
            updateBodyPartFactorText()
            calculateManualSar()
            updateHistorySummary()
        }
    }

    private fun observeData() {
        // Actualizar datos periódicamente
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                try {
                    val sarValue = sarManager.getCurrentSARLevel()
                    binding.apply {
                        sarValueText.text = String.format(java.util.Locale.getDefault(), "%.2f W/kg", sarValue)
                        // Actualizar indicador visual si existe
                        sarIndicator.setBackgroundColor(
                            when {
                                sarValue > 1.6 -> android.graphics.Color.RED
                                sarValue > 1.0 -> android.graphics.Color.YELLOW
                                else -> android.graphics.Color.GREEN
                            }
                        )
                        // Actualizar barra de progreso si existe
                        val rawProgress = (sarValue / 1.6 * 100).toInt().coerceIn(0, 100)
                        // Asegurar un progreso mínimo visible para que no parezca insignificante
                        val displayProgress = when {
                            sarValue == 0.0 -> 0 // 0% si realmente no hay SAR
                            rawProgress == 0 && sarValue > 0.0 -> 3 // Mínimo 3% si hay SAR pero cálculo da 0
                            rawProgress in 1..4 -> 5 // Si está entre 1-4%, mostrar mínimo 5%
                            else -> rawProgress // Mantener valor original para valores normales
                        }
                        sarProgressBar.progress = displayProgress
                        updateRiskAndSafety(sarValue)
                    }
                } catch (e: Exception) {
                    // getString(R.string.comment_handle_errors)
                }
                val intervalMillis = com.exposiguard.app.utils.AppEvents.getMonitoringIntervalMillis(requireContext())
                kotlinx.coroutines.delay(intervalMillis)
            }
        }
    }

    private fun updateSarChart(@Suppress("UNUSED_PARAMETER") history: List<Double>) {
        // Implementar actualización del gráfico si existe
        // Por ahora solo mostrar el último valor
    }

    // BMI ya no se muestra aquí; se gestiona en Configuración

    private fun currentStandardKey(): String {
        val saved = userProfileManager.getExposureStandard()
        return if (saved.equals("ICNIRP", true)) "ICNIRP" else "FCC"
    }

    private fun updateStandardLimitText() {
    val limit = if (currentStandardKey() == "ICNIRP") 2.0 else 1.6
    binding.standardLimitText.text = getString(R.string.sar_limit_value_format, limit)
    }

    private fun updateBodyPartFactorText() {
        val factor = when (binding.bodyPartSpinner.selectedItemPosition) {
            0 -> 1.2 // ${getString(R.string.term_head)}
            1 -> 1.0 // ${getString(R.string.term_body)}
            else -> 0.8 // ${getString(R.string.term_hand)}
        }
        binding.bodyPartFactorText.text = getString(R.string.sar_factor_value_format, factor)
    }

    private fun calculateManualSar() {
    val profile = userProfileManager.getUserProfile()
    val weight = profile.weight
    val height = profile.height
        val signalDbm = binding.signalStrengthSlider.value.toInt()
        val distance = binding.distanceSlider.value.toDouble()
        val standardKey = currentStandardKey()

    val absorption = emfManager.calculateAbsorptionFactor(EMFManager.UserProfile(weight, height, imc = 0.0))

        // Aproximar SAR con WiFi formula para señal/distancia (sin depender de lista de operadores)
        val sarWiFiLike = emfManager.calculateWiFiSAR(signalDbm, distance.coerceAtLeast(0.1), absorption)

        // Aplicar factor por parte del cuerpo
        val bodyFactor = when (binding.bodyPartSpinner.selectedItemPosition) {
            0 -> 1.2
            1 -> 1.0
            else -> 0.8
        }
        val sarValue = (sarWiFiLike * bodyFactor).coerceAtLeast(0.0)

    binding.sarValueText.text = String.format(java.util.Locale.getDefault(), "%.2f W/kg", sarValue)
        val rawProgress = (sarValue / (if (standardKey == "ICNIRP") 2.0 else 1.6) * 100).toInt().coerceIn(0, 100)
        // Asegurar un progreso mínimo visible para que no parezca insignificante
        val displayProgress = when {
            sarValue == 0.0 -> 0 // 0% si realmente no hay SAR
            rawProgress == 0 && sarValue > 0.0 -> 3 // Mínimo 3% si hay SAR pero cálculo da 0
            rawProgress in 1..4 -> 5 // Si está entre 1-4%, mostrar mínimo 5%
            else -> rawProgress // Mantener valor original para valores normales
        }
        binding.sarProgressBar.progress = displayProgress
        binding.sarIndicator.setBackgroundColor(
            when {
                sarValue > (if (standardKey == "ICNIRP") 2.0 else 1.6) -> android.graphics.Color.RED
                sarValue > 1.0 -> android.graphics.Color.YELLOW
                else -> android.graphics.Color.GREEN
            }
        )
        updateRiskAndSafety(sarValue)
    }

    private fun updateHistorySummary() {
        // Calcular TWA y Máx de SAR en el rango seleccionado usando ExposureRepository
        val endTime = System.currentTimeMillis()
        val windowMs = when (binding.rangeToggle.checkedButtonId) {
            binding.range5h.id -> 5L * 60 * 60 * 1000
            binding.range24h.id -> 24L * 60 * 60 * 1000
            binding.range30d.id -> 30L * 24 * 60 * 60 * 1000
            else -> 5L * 60 * 60 * 1000
        }
        val startTime = endTime - windowMs

        // maxGapMs atado al intervalo configurado en Settings (3×)
        val settingsPrefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
        val intervalMinutes = settingsPrefs.getString("monitoring_interval", "5")?.toLongOrNull() ?: 5L
        val maxGapMs = intervalMinutes * 60_000L * 3L

        // Ejecutar el cálculo fuera del hilo principal
        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val sarTwa = exposureRepository
                .timeWeightedAverageComponents(startTime, endTime, maxGapMs)
                .second
            val readings = exposureRepository.getReadingsInTimeRange(startTime, endTime)
            val maxSar = readings.maxOfOrNull { it.sarLevel } ?: 0.0

            // Downsampling para sparkline (hasta 60 puntos)
            val points = downsampleSarSeries(readings, maxPoints = 60, startTime, endTime)

            val summary = getString(R.string.sar_history_summary_placeholder)
                .replace("Promedio/TWA:", getString(R.string.sar_history_average))
                .replace("--", String.format(java.util.Locale.getDefault(), "%.2f", sarTwa))
                .replace("Máx:", getString(R.string.sar_history_max))
                .replace("--", String.format(java.util.Locale.getDefault(), "%.2f", maxSar))

            // Publicar en UI
            with(kotlinx.coroutines.Dispatchers.Main) {
                viewLifecycleOwner.lifecycleScope.launch(this) {
                    binding.sarHistorySummaryText.text = summary
                    binding.sarSparkline.setData(points)
                }
            }
        }
    }

    private fun downsampleSarSeries(
        readings: List<com.exposiguard.app.data.ExposureReading>,
        maxPoints: Int,
        start: Long,
        end: Long
    ): List<Double> {
        if (readings.isEmpty() || maxPoints <= 1) return emptyList()
        val sorted = readings.sortedBy { it.timestamp }
        val buckets = maxPoints
        val window = (end - start).coerceAtLeast(1L)
        val bucketSize = window / buckets
        if (bucketSize <= 0) return sorted.map { it.sarLevel }

        val result = ArrayList<Double>(buckets)
        var bucketStart = start
        for (i in 0 until buckets) {
            val bucketEnd = if (i == buckets - 1) end else (bucketStart + bucketSize)
            // promedio simple del bucket (o último valor si vacío)
            val slice = sorted.filter { it.timestamp in bucketStart..bucketEnd }
            val v = when {
                slice.isNotEmpty() -> slice.map { it.sarLevel }.average()
                else -> result.lastOrNull() ?: 0.0
            }
            result.add(v)
            bucketStart = bucketEnd + 1
        }
        return result
    }

    private fun updateRiskAndSafety(sarValue: Double) {
        val standardKey = currentStandardKey()
        val limit = if (standardKey == "ICNIRP") 2.0 else 1.6
        val risk = emfManager.evaluateRiskLevel(sarValue, limit)
        binding.riskLevelText.text = when (risk) {
            "BAJO" -> getString(com.exposiguard.app.R.string.risk_low)
            "MODERADO" -> getString(com.exposiguard.app.R.string.risk_moderate)
            "ALTO" -> getString(com.exposiguard.app.R.string.risk_high)
            "CRÍTICO" -> getString(com.exposiguard.app.R.string.risk_critical)
            else -> risk
        }
        val safety = (100.0 * (1.0 - (sarValue / limit))).coerceIn(0.0, 100.0)
    binding.safetyMarginText.text = String.format(java.util.Locale.getDefault(), "%.1f%%", safety)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
