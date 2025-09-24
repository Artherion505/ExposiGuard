package com.exposiguard.app.features.trends.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.exposiguard.app.R
import com.exposiguard.app.databinding.FragmentTrendsBinding
import com.exposiguard.app.managers.SARManager
import com.exposiguard.app.managers.TrendsAnalysisManager
import com.exposiguard.app.managers.UserProfileManager
import com.exposiguard.app.managers.WiFiManager
import com.exposiguard.app.repository.ExposureRepository
import java.text.SimpleDateFormat
import java.util.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@AndroidEntryPoint
class TrendsFragment : Fragment() {

    private var _binding: FragmentTrendsBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var trendsManager: TrendsAnalysisManager
    @Inject lateinit var exposureRepository: ExposureRepository
    @Inject lateinit var wiFiManager: WiFiManager
    @Inject lateinit var sarManager: SARManager
    @Inject lateinit var userProfileManager: UserProfileManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Los managers ya están inyectados por Hilt
        loadTrendsData()

        // Escuchar eventos para refrescar
        com.exposiguard.app.utils.AppEvents.events
            .onEach {
                when (it) {
                    is com.exposiguard.app.utils.AppEvents.Event.DataChanged,
                    is com.exposiguard.app.utils.AppEvents.Event.SettingsChanged -> {
                        loadTrendsData()
                        try { com.google.android.material.snackbar.Snackbar.make(binding.root, "Actualizado", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() } catch (_: Exception) {}
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    // Eliminado: no inyectar lecturas sintéticas desde este fragment.

    private fun loadTrendsData() {
        val readings = exposureRepository.getAllReadings()
        val combinedData = trendsManager.getCombinedExposureData(readings)

        // Display enhanced analysis text
        val analysis = generateEnhancedAnalysis(readings)
        binding.textTrendsAnalysis.text = analysis

        // Create chart bars
        createChart(combinedData)
    }

    private fun generateEnhancedAnalysis(readings: List<com.exposiguard.app.data.ExposureReading>): String {
        if (readings.isEmpty()) {
            return getString(R.string.trends_no_data_analysis)
        }

    val analysis = buildString {
            append(getString(R.string.trends_analysis_title))
            append("\n\n")

            // Información del perfil del usuario
            val userWeight = userProfileManager.getWeight()
            val userHeight = userProfileManager.getHeight()
            append(getString(R.string.trends_user_profile))
            append("\n")
            append(getString(R.string.trends_weight))
            append(" ${String.format(java.util.Locale.getDefault(), "%.1f", userWeight)} kg\n")
            append(getString(R.string.trends_height))
            append(" ${String.format(java.util.Locale.getDefault(), "%.1f", userHeight)} cm\n")
            append(getString(R.string.trends_status))
            append(" ${if (userProfileManager.isProfileConfigured()) getString(R.string.trends_configured) else getString(R.string.trends_default_values)}\n\n")

            // Estadísticas de exposición (TWA con zero-order hold y maxGap atado a Settings)
            val endTime = System.currentTimeMillis()
            val startTime = (readings.minOfOrNull { it.timestamp } ?: endTime - 24 * 60 * 60 * 1000L)
            val settingsPrefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
            val intervalMinutes = settingsPrefs.getString("monitoring_interval", "5")?.toLongOrNull() ?: 5L
            val (twaWifi, twaSar, twaBt) = exposureRepository.timeWeightedAverageComponents(
                startTime,
                endTime,
                (intervalMinutes * 3L).coerceAtLeast(1L) * 60_000L
            )
            val avgWifi = twaWifi
            val avgSar = twaSar
            val avgBt = twaBt
            val maxExposure = readings.maxOf { it.wifiLevel + it.sarLevel + it.bluetoothLevel }

            append(getString(R.string.trends_exposure_stats))
            append("\n")
            append(getString(R.string.trends_wifi_twa))
            append(" ${String.format(java.util.Locale.getDefault(), "%.2f", avgWifi)}\n")
            append(getString(R.string.trends_sar_twa))
            append(" ${String.format(java.util.Locale.getDefault(), "%.2f", avgSar)} W/kg\n")
            append(getString(R.string.trends_bluetooth_twa))
            append(" ${String.format(java.util.Locale.getDefault(), "%.2f", avgBt)} W/kg\n")
            append(getString(R.string.trends_max_exposure))
            append(" ${String.format(java.util.Locale.getDefault(), "%.2f", maxExposure)}\n\n")

            // Análisis de tendencias
            val trendAnalysis = trendsManager.analyzeTrends(readings)
            append(getString(R.string.trends_trend_analysis))
            append("\n")
            append(trendAnalysis.toString())
            append("\n")

            // Recomendaciones basadas en datos reales
            append(getString(R.string.trends_recommendations))
            append("\n")
            when {
                avgSar > 1.6 -> append(getString(R.string.trends_rec_high_sar))
                avgWifi > 0.5 -> append(getString(R.string.trends_rec_high_wifi))
                avgBt > 0.2 -> append(getString(R.string.trends_rec_bluetooth))
                maxExposure > 2.0 -> append(getString(R.string.trends_rec_peaks))
                else -> append(getString(R.string.trends_rec_safe))
            }
            append("\n")

            if (!userProfileManager.isProfileConfigured()) {
                append(getString(R.string.trends_rec_configure))
                append("\n")
            }
        }

        return analysis
    }

    private fun createChart(data: List<com.exposiguard.app.data.CombinedExposureReading>) {
        val container = binding.chartBarsContainer
        container.removeAllViews()

        if (data.isEmpty()) {
            val noDataText = TextView(requireContext()).apply {
                text = getString(R.string.trends_no_chart_data)
                textSize = 14f
                setPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(noDataText)
            return
        }

    // Para comparar contra el límite legal (W/kg), usar solamente SAR+Bluetooth
    val maxSarLike = data.maxOfOrNull { it.sarLevel + it.bluetoothLevel } ?: 1.0
    val recommendedLimit = getRecommendedLimit()
    val chartMax = maxOf(maxSarLike, recommendedLimit * 1.2) // 20% más que el límite recomendado

        // Crear contenedor principal con mejor espaciado
        val mainContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Agregar línea de referencia para el límite recomendado
        val referenceContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        val referenceLine = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                2,
                1f
            )
            setBackgroundColor(Color.RED)
        }

        val referenceLabel = TextView(requireContext()).apply {
            text = getString(R.string.trends_recommended_limit) + " ${String.format(java.util.Locale.getDefault(), "%.2f", recommendedLimit)} W/kg"
            textSize = 12f
            setTextColor(Color.RED)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 0, 0, 0)
            }
        }

        referenceContainer.addView(referenceLine)
        referenceContainer.addView(referenceLabel)
        mainContainer.addView(referenceContainer)

        // Crear barras mejoradas
        val barsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                300 // Altura fija para mejor consistencia
            ).apply {
                setMargins(0, 8, 0, 8)
            }
            setPadding(8, 0, 8, 0)
        }

        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        data.forEachIndexed { index, reading ->
            val barContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, // Usar weight para distribución equitativa
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f // Peso igual para todas las barras
                ).apply {
                    setMargins(2, 0, 2, 0)
                }
            }

            // Barra principal
            val sarLike = (reading.sarLevel + reading.bluetoothLevel)
            val barHeight = (sarLike / chartMax * 200).toInt().coerceAtLeast(8)
            val bar = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    barHeight
                )
                setBackgroundColor(getBarColor(sarLike, recommendedLimit))
                // Bordes redondeados
                background = createRoundedBackground(getBarColor(sarLike, recommendedLimit))
            }

            // Etiqueta de valor en la parte superior de la barra
            val valueLabel = TextView(requireContext()).apply {
                text = String.format(java.util.Locale.getDefault(), "%.2f", sarLike)
                textSize = 10f
                // Evitar texto negro sobre fondo oscuro en modo noche
                setTextColor(Color.parseColor("#DDFFFFFF"))
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 2)
                }
            }

            // Etiqueta de tiempo en la parte inferior
            val timeLabel = TextView(requireContext()).apply {
                text = if (index % 3 == 0) { // Mostrar cada 3 barras para evitar clutter
                    timeFormat.format(Date(reading.timestamp))
                } else {
                    dateFormat.format(Date(reading.timestamp))
                }
                textSize = 9f
                setTextColor(Color.GRAY)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 2, 0, 4)
                }
            }

            barContainer.addView(valueLabel)
            barContainer.addView(bar)
            barContainer.addView(timeLabel)

            barsContainer.addView(barContainer)
        }

        mainContainer.addView(barsContainer)

        // Agregar información adicional debajo de la gráfica
        val infoContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 0)
            }
        }

        val statsText = TextView(requireContext()).apply {
            text = buildString {
                append(getString(R.string.trends_chart_summary))
                append("\n")
                append(getString(R.string.trends_max_sar_bt))
                append(" ${String.format(java.util.Locale.getDefault(), "%.2f", maxSarLike)} W/kg\n")
                append(getString(R.string.trends_recommended_limit))
                append(" ${String.format(java.util.Locale.getDefault(), "%.2f", recommendedLimit)} W/kg\n")
                append(getString(R.string.trends_status_label))
                append(" ${if (maxSarLike > recommendedLimit) getString(R.string.trends_exceeding_limit) else getString(R.string.trends_within_limit)}\n")
                append(getString(R.string.trends_measurement_count))
                append(" ${data.size}")
            }
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        infoContainer.addView(statsText)
        mainContainer.addView(infoContainer)

        container.addView(mainContainer)
    }

    private fun getBarColor(exposure: Double, recommendedLimit: Double): Int {
        return when {
            exposure < recommendedLimit * 0.5 -> Color.GREEN // Verde: muy por debajo del límite
            exposure < recommendedLimit * 0.8 -> Color.rgb(255, 255, 0) // Amarillo: cercano al límite
            exposure < recommendedLimit -> Color.rgb(255, 165, 0) // Naranja: cerca del límite
            exposure < recommendedLimit * 1.2 -> Color.RED // Rojo: superando límite
            else -> Color.rgb(139, 0, 0) // Rojo oscuro: muy por encima del límite
        }
    }

    private fun getRecommendedLimit(): Double {
        // Obtener el estándar seleccionado desde SharedPreferences
        val sharedPrefs = requireContext().getSharedPreferences("exposiguard_prefs", android.content.Context.MODE_PRIVATE)
        val standard = sharedPrefs.getString("exposure_standard", "FCC") ?: "FCC"

        // Límite base según estándar (valores típicos para adultos)
        val baseLimit = when (standard) {
            "ICNIRP" -> 2.0 // W/kg para ICNIRP
            "FCC" -> 1.6 // W/kg para FCC
            else -> 1.6
        }
        // No escalar por talla: los límites legales son absolutos por estándar
        return baseLimit
    }

    private fun createRoundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 8f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
