package com.exposiguard.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.exposiguard.app.databinding.FragmentTrendsBinding
import com.exposiguard.app.managers.SARManager
import com.exposiguard.app.managers.TrendsAnalysisManager
import com.exposiguard.app.managers.UserProfileManager
import com.exposiguard.app.managers.WiFiManager
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.exposiguard.app.R
import com.exposiguard.app.data.CombinedExposureReading
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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

        try {
            // Los managers ya están inyectados por Hilt
            // Configurar leyenda con el tag localizado de backfill (relleno 5 min)
            try {
                val tag = getString(com.exposiguard.app.R.string.trends_backfill_tag)
                val legend = getString(com.exposiguard.app.R.string.trends_legend_backfill, tag)
                binding.root.findViewById<TextView>(com.exposiguard.app.R.id.text_trends_legend)?.text = legend
            } catch (_: Exception) { }
            loadTrendsData()

            // Escuchar eventos para refrescar
            com.exposiguard.app.utils.AppEvents.events
                .onEach {
                    when (it) {
                        is com.exposiguard.app.utils.AppEvents.Event.DataChanged,
                        is com.exposiguard.app.utils.AppEvents.Event.SettingsChanged -> {
                            loadTrendsData()
                            try { com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.trends_updated), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() } catch (_: Exception) {}
                        }
                    }
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)
        } catch (e: Exception) {
            android.util.Log.e("TrendsFragment", "Error initializing TrendsFragment", e)
            binding.textTrendsAnalysis.text = getString(R.string.trends_error_initialization, e.message ?: "Unknown error")
        }
    }

    // Eliminado: no inyectar lecturas sintéticas desde Trends. Usar las reales del repositorio.

    private fun loadTrendsData() {
        try {
            android.util.Log.d("TrendsFragment", "Loading trends data...")
            val readings = exposureRepository.getAllReadings()
            android.util.Log.d("TrendsFragment", "Retrieved ${readings.size} readings from repository")

            val combinedData = trendsManager.getCombinedExposureData(readings)
            android.util.Log.d("TrendsFragment", "Converted to ${combinedData.size} combined data points")

            // Display enhanced analysis text with null safety
            val analysis = generateEnhancedAnalysis(readings)
            binding.textTrendsAnalysis.text = analysis

            // Crear gráfico estilo ritmo cardiaco con ventana basada en Settings
            createSimpleHeartRateChart()
        } catch (e: Exception) {
            android.util.Log.e("TrendsFragment", "Error loading trends data: ${e.message}", e)
            // getString(R.string.comment_fallback_show_error_message)
            try {
                binding.textTrendsAnalysis.text = getString(R.string.trends_error_loading_data, e.message ?: "Unknown error")
            } catch (bindingException: Exception) {
                android.util.Log.e("TrendsFragment", "Error updating UI: ${bindingException.message}")
            }
        }
    }

    private fun generateEnhancedAnalysis(readings: List<ExposureReading>): String {
        if (readings.isEmpty()) {
            return getString(R.string.trends_no_data_analysis)
        }

        val analysis = buildString {
            append(getString(R.string.trends_analysis_title) + "\n\n")

            // Información del perfil del usuario
            val userWeight = userProfileManager.getWeight()
            val userHeight = userProfileManager.getHeight()
            append(getString(R.string.trends_user_profile) + "\n")
            append(getString(R.string.trends_weight) + " ${String.format(java.util.Locale.getDefault(), "%.1f", userWeight)} kg\n")
            append(getString(R.string.trends_height) + " ${String.format(java.util.Locale.getDefault(), "%.1f", userHeight)} cm\n")
            append(getString(R.string.trends_status) + " ${if (userProfileManager.isProfileConfigured()) getString(R.string.trends_configured) else getString(R.string.trends_default_values)}\n\n")

            // Estadísticas de exposición con TWA atado al intervalo configurado
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
            val avgBluetooth = twaBt
            val maxExposure = readings.maxOf { it.wifiLevel + it.sarLevel + it.bluetoothLevel }

            append(getString(R.string.trends_exposure_stats) + "\n")
            append("• WiFi TWA: ${String.format(java.util.Locale.getDefault(), "%.2f", avgWifi)}\n")
            append("• SAR TWA: ${String.format(java.util.Locale.getDefault(), "%.2f", avgSar)} W/kg\n")
            append("• Bluetooth TWA: ${String.format(java.util.Locale.getDefault(), "%.2f", avgBluetooth)}\n")
            append("• " + getString(R.string.trends_max_exposure) + " ${String.format(java.util.Locale.getDefault(), "%.2f", maxExposure)}\n\n")

            // Análisis de tendencias
            val trendAnalysis = trendsManager.analyzeTrends(readings)
            append(getString(R.string.trends_trend_analysis) + "\n")
            append(trendAnalysis.toString())
            append("\n")

            // Recomendaciones basadas en datos reales
            append(getString(R.string.trends_recommendations) + "\n")
            when {
                avgSar > 1.6 -> append(getString(R.string.trends_rec_high_sar) + "\n")
                avgWifi > 0.5 -> append(getString(R.string.trends_rec_high_wifi) + "\n")
                avgBluetooth > 0.2 -> append(getString(R.string.trends_rec_bluetooth) + "\n")
                maxExposure > 2.0 -> append(getString(R.string.trends_rec_peaks) + "\n")
                else -> append(getString(R.string.trends_rec_safe) + "\n")
            }

            if (!userProfileManager.isProfileConfigured()) {
                append(getString(R.string.trends_rec_configure) + "\n")
            }
        }

        return analysis
    }

    private fun createSimpleHeartRateChart() {
        val ctx = context ?: return
        val container = binding.chartBarsContainer
        container.removeAllViews()

        val settingsPrefs = requireContext().getSharedPreferences("exposiguard_settings", android.content.Context.MODE_PRIVATE)
        val intervalMin = settingsPrefs.getString("monitoring_interval", "5")?.toLongOrNull() ?: 5L

        // Regla de ventana según solicitud
        val windowMs = when (intervalMin) {
            1L -> 10L * 60_000L   // 10 min
            5L -> 30L * 60_000L   // 30 min
            10L -> 60L * 60_000L  // 60 min
            30L -> 6L * 60 * 60_000L // 6 horas
            60L -> 12L * 60 * 60_000L // 12 horas
            else -> 60L * 60_000L // fallback 60 min
        }

        val end = System.currentTimeMillis()
        val start = end - windowMs

        // Obtener lecturas en rango y agregarlas por buckets de 5 minutos combinando componentes
        val readings = exposureRepository.getReadingsInTimeRange(start, end)
        val sortedReadings = readings.sortedBy { it.timestamp }

        // Agregación a buckets de 5 minutos: combinar múltiples lecturas y evitar ceros espurios
        val bucketMs = 5L * 60_000L
        fun roundDown(t: Long) = t - (t % bucketMs)
        val buckets: List<CombinedExposureReading> = if (sortedReadings.isEmpty()) emptyList() else run {
            val startBucket = roundDown(sortedReadings.first().timestamp)
            val endBucket = roundDown(end)
            val byTime = sortedReadings.groupBy { roundDown(it.timestamp) }
            val result = ArrayList<CombinedExposureReading>()
            var t = startBucket
            var lastSar = 0.0
            var lastSarTs = -1L
            while (t <= endBucket) {
                val slice = byTime[t].orEmpty()
                val wifiAvg = slice.map { it.wifiLevel }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                val sarAvgRaw = slice.map { it.sarLevel }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
                val btAvgRaw = slice.map { it.bluetoothLevel }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()

                val sarAvg = sarAvgRaw ?: run {
                    // Carry-forward durante 2 buckets (10 min) si no hay SAR en este bucket
                    if (lastSarTs >= 0 && (t - lastSarTs) <= (bucketMs * 2)) lastSar else 0.0
                }
                val btAvg = btAvgRaw ?: 0.0
                if (sarAvgRaw != null) { lastSar = sarAvgRaw; lastSarTs = t }

                result.add(
                    CombinedExposureReading(
                        timestamp = t,
                        totalExposure = wifiAvg + sarAvg + btAvg,
                        wifiLevel = wifiAvg,
                        sarLevel = sarAvg,
                        bluetoothLevel = btAvg,
                        source = if (slice.any { it.source.contains("Backfill", ignoreCase = true) }) "Backfill" else ""
                    )
                )
                t += bucketMs
            }
            result
        }

        // Serie para sparkline en W/kg (usar SAR+Bluetooth)
        val series = buckets.map { it.sarLevel + it.bluetoothLevel }

        // Si no hay lecturas, mostrar mensaje
        if (series.isEmpty()) {
            val noDataText = TextView(ctx).apply {
                text = getString(R.string.trends_no_data_selected_window)
                textSize = 16f
                setTextColor(android.graphics.Color.parseColor("#BBBBBB"))
                setPadding(32, 32, 32, 32)
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(noDataText)
            return
        }

        // Downsample a ~120 puntos para “ritmo cardiaco”
        val downsampled = downsample(series, 120)

        // Render con SparklineView reutilizable (línea limpia estilo ECG)
        val spark = com.exposiguard.app.ui.views.SparklineView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                240
            ).apply { setMargins(16, 16, 16, 16) }
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#121212")) // fondo oscuro para encajar en modo noche
            setData(downsampled)
        }

        container.addView(spark)

        // Añadir gráfico ECG interactivo con tooltips y marcado de backfill
        try {
            val combined = buckets
            val limit = getRecommendedLimit()
            val ecg = ECGChartView(ctx, combined, limit, getString(R.string.no_data_available)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    260
                ).apply { setMargins(16, 0, 16, 16) }
                setPadding(16, 16, 16, 16)
                setBackgroundColor(android.graphics.Color.parseColor("#0E0E0E"))
            }
            container.addView(ecg)
        } catch (e: Exception) {
            android.util.Log.w("TrendsFragment", "ECGChartView creation failed: ${e.message}")
        }
    }

    private fun downsample(values: List<Double>, maxPoints: Int): List<Double> {
        if (values.isEmpty() || values.size <= maxPoints) return values
        val bucketSize = values.size.toDouble() / maxPoints
        val result = ArrayList<Double>(maxPoints)
        var acc = 0.0
        var count = 0
        var nextBucket = bucketSize
        var idx = 0
        var target = 0.0
        while (idx < values.size) {
            acc += values[idx]
            count++
            target += 1
            if (target >= nextBucket || idx == values.lastIndex) {
                result.add(acc / count)
                acc = 0.0
                count = 0
                nextBucket += bucketSize
            }
            idx++
        }
        return result
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

        // Calcular límite basado en peso y altura del usuario
        val weight = userProfileManager.getWeight()
        val height = userProfileManager.getHeight()

        // Límite base según estándar (valores típicos para adultos)
        val baseLimit = when (standard) {
            "ICNIRP" -> 2.0 // W/kg para ICNIRP
            "FCC" -> 1.6 // W/kg para FCC
            else -> 1.6
        }

        // Ajustar según peso y altura (personas más pequeñas pueden tener límites más bajos)
        val sizeFactor = (weight / 70.0) * (height / 170.0) // Normalizado a persona promedio
        return baseLimit * sizeFactor.coerceIn(0.5, 1.5) // Limitar el rango de ajuste
    }

    private fun createRoundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = 8f
        }
    }

    private fun safeParseColor(colorString: String, fallbackColor: Int = Color.GRAY): Int {
        return try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            android.util.Log.w("TrendsFragment", "Error parsing color: $colorString, using fallback", e)
            fallbackColor
        }
    }

    private fun createGradientBar(color: Int): GradientDrawable {
        return try {
            val lighterColor = try {
                // Crear un color más claro de manera segura
                val alpha = 0x80
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                Color.argb(alpha, red, green, blue)
            } catch (e: Exception) {
                android.util.Log.w("TrendsFragment", "Error creating lighter color, using original", e)
                color
            }

            GradientDrawable().apply {
                colors = intArrayOf(color, lighterColor)
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                cornerRadius = 6f
            }
        } catch (e: Exception) {
            android.util.Log.w("TrendsFragment", "Error creating gradient bar, using solid color", e)
            GradientDrawable().apply {
                setColor(color)
                cornerRadius = 6f
            }
        }
    }

    private fun getRiskEmoji(exposure: Double, limit: Double): String {
        return when {
            exposure >= limit * 1.5 -> "🔴" // Crítico
            exposure >= limit -> "🟠" // Alto
            exposure >= limit * 0.7 -> "🟡" // Moderado
            else -> "🟢" // Bajo
        }
    }

    private fun getOverallStatus(maxExposure: Double, limit: Double): String {
        return when {
            maxExposure >= limit * 1.5 -> getString(R.string.trends_critical_status)
            maxExposure >= limit -> getString(R.string.trends_high_status)
            maxExposure >= limit * 0.7 -> getString(R.string.trends_moderate_status)
            else -> getString(R.string.trends_safe_status)
        }
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        android.util.Log.d("TrendsFragment", "Fragment attached to context")
    }

    override fun onDetach() {
        super.onDetach()
        android.util.Log.d("TrendsFragment", "Fragment detached from context")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        android.util.Log.d("TrendsFragment", "View destroyed and binding cleared")
    }
}

// Clase personalizada para el gráfico de ECG estilo frecuencia cardíaca
class ECGChartView(
    context: android.content.Context,
    private val data: List<CombinedExposureReading>,
    private val recommendedLimit: Double,
    private val noDataMessage: String = "No hay datos disponibles"
) : View(context) {

    private val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        strokeWidth = 3f
        style = android.graphics.Paint.Style.STROKE
    }

    private val gridPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(30, 255, 255, 255)
        strokeWidth = 1f
        style = android.graphics.Paint.Style.STROKE
    }

    private val pointPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
    }

    private val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 24f
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
    }

    private val path = android.graphics.Path()
    private var maxValue = 0.0
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private var downX = 0f
    private var downY = 0f
    private val touchSlop by lazy { android.view.ViewConfiguration.get(context).scaledTouchSlop }

    init {
        // Para W/kg, considerar sólo SAR+Bluetooth
        maxValue = data.maxOfOrNull { it.sarLevel + it.bluetoothLevel } ?: 1.0
        if (maxValue < recommendedLimit * 1.5) {
            maxValue = recommendedLimit * 1.5
        }

        // No bloquear scroll del padre: sólo manejar taps ligeros
        isClickable = true
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y
                    // permitir que el padre decida por ahora
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(event.x - downX)
                    val dy = kotlin.math.abs(event.y - downY)
                    if (dy > dx && dy > touchSlop) {
                        // gesto vertical: dejar que el padre intercepte
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        return@setOnTouchListener false
                    } else {
                        // gesto horizontal o tap: no interceptar
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        return@setOnTouchListener false
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val dx = kotlin.math.abs(event.x - downX)
                    val dy = kotlin.math.abs(event.y - downY)
                    if (dx < touchSlop && dy < touchSlop) {
                        try { handleTouch(event.x, event.y) } catch (_: Exception) {}
                    }
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                    false
                }
                else -> false
            }
        }
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        android.util.Log.d("ECGChartView", "onDraw called with data size: ${data.size}")

        if (data.isEmpty()) {
            android.util.Log.w("ECGChartView", "No data available for chart")
            // Dibujar mensaje cuando no hay datos
            textPaint.color = android.graphics.Color.WHITE
            textPaint.textSize = 20f
            canvas.drawText(noDataMessage, width / 2f, height / 2f, textPaint)
            return
        }

        val width = width.toFloat()
        val height = height.toFloat()
        val padding = 60f

        android.util.Log.d("ECGChartView", "Drawing chart with dimensions: ${width}x${height}")

        // Dibujar cuadrícula de fondo
        drawGrid(canvas, width, height, padding)

        // Dibujar línea del límite recomendado
        drawLimitLine(canvas, width, height, padding)

        // Dibujar el gráfico ECG
        drawECGLine(canvas, width, height, padding)

        // Dibujar puntos de datos
        drawDataPoints(canvas, width, height, padding)

        // Dibujar etiquetas
        drawLabels(canvas, width, height, padding)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleTouch(xTouch: Float, yTouch: Float) {
        if (data.isEmpty() || width <= 0) return
        val padding = 60f
        val chartWidth = (width - 2 * padding).coerceAtLeast(1f)
        val relativeX = (xTouch - padding).coerceIn(0f, chartWidth)
        val ratio = relativeX / chartWidth
        val index = (ratio * (data.size - 1)).toInt().coerceIn(0, data.size - 1)
        val r = data[index]
        val backfillTag = if (r.source.contains("Backfill", ignoreCase = true))
            try { context.getString(com.exposiguard.app.R.string.trends_backfill_tag) } catch (_: Exception) { " (relleno 5 min)" }
        else ""
        val msg = try {
            context.getString(
                com.exposiguard.app.R.string.trends_point_tooltip,
                dateFormat.format(Date(r.timestamp)),
                (r.sarLevel + r.bluetoothLevel),
                backfillTag
            )
        } catch (_: Exception) {
            "${dateFormat.format(Date(r.timestamp))} • ${String.format(java.util.Locale.getDefault(), "%.3f", (r.sarLevel + r.bluetoothLevel))} W/kg $backfillTag"
        }
        try {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    private fun drawGrid(canvas: android.graphics.Canvas, width: Float, height: Float, padding: Float) {
        val gridSpacing = 40f

        // Líneas verticales
        for (x in padding.toInt()..width.toInt() step gridSpacing.toInt()) {
            canvas.drawLine(x.toFloat(), padding, x.toFloat(), height - padding, gridPaint)
        }

        // Líneas horizontales
        for (y in padding.toInt()..(height - padding).toInt() step gridSpacing.toInt()) {
            canvas.drawLine(padding, y.toFloat(), width - padding, y.toFloat(), gridPaint)
        }
    }

    private fun drawLimitLine(canvas: android.graphics.Canvas, width: Float, height: Float, padding: Float) {
        val limitY = height - padding - ((recommendedLimit.toFloat() / maxValue.toFloat()) * (height - 2 * padding))

        paint.color = android.graphics.Color.argb(150, 255, 165, 0) // Naranja semi-transparente
        paint.strokeWidth = 2f
        canvas.drawLine(padding, limitY, width - padding, limitY, paint)

        // Etiqueta del límite
        textPaint.color = android.graphics.Color.rgb(255, 165, 0)
        textPaint.textSize = 20f
    canvas.drawText("Límite: ${String.format(java.util.Locale.getDefault(), "%.2f", recommendedLimit)} W/kg",
                       width - padding - 150f, limitY - 10f, textPaint)
    }

    private fun drawECGLine(canvas: android.graphics.Canvas, width: Float, height: Float, padding: Float) {
        if (data.size < 2) return

        path.reset()

        val chartWidth = width - 2 * padding
        val chartHeight = height - 2 * padding

        // Calcular puntos
        val points = mutableListOf<android.graphics.PointF>()

        data.forEachIndexed { index, reading ->
            val x = padding + (index.toFloat() / (data.size - 1)) * chartWidth
            val y = height - padding - ((((reading.sarLevel + reading.bluetoothLevel).toFloat()) / maxValue.toFloat()) * chartHeight)
            points.add(android.graphics.PointF(x, y))
        }

        // Crear línea curva usando Path
        if (points.size >= 2) {
            path.moveTo(points[0].x, points[0].y)

            for (i in 1 until points.size) {
                val current = points[i]
                val previous = points[i - 1]

                // Crear curva suave entre puntos
                val midX = (previous.x + current.x) / 2
                val midY = (previous.y + current.y) / 2

                if (i == 1) {
                    path.lineTo(midX, midY)
                } else {
                    path.quadTo(previous.x, previous.y, midX, midY)
                }

                if (i == points.size - 1) {
                    path.lineTo(current.x, current.y)
                }
            }
        }

        // Dibujar la línea con gradiente de color basado en el riesgo
        paint.strokeWidth = 4f
        paint.style = android.graphics.Paint.Style.STROKE

        val colors = intArrayOf(
            android.graphics.Color.rgb(255, 100, 100), // Rojo claro
            android.graphics.Color.rgb(255, 150, 150), // Rojo medio
            android.graphics.Color.rgb(255, 200, 200)  // Rojo claro
        )

        val shader = android.graphics.LinearGradient(
            0f, 0f, width, 0f,
            colors,
            null,
            android.graphics.Shader.TileMode.CLAMP
        )

        paint.shader = shader
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun drawDataPoints(canvas: android.graphics.Canvas, width: Float, height: Float, padding: Float) {
        val chartWidth = width - 2 * padding
        val chartHeight = height - 2 * padding

        data.forEachIndexed { index, reading ->
            val x = padding + (index.toFloat() / (data.size - 1)) * chartWidth
            val y = height - padding - ((((reading.sarLevel + reading.bluetoothLevel).toFloat()) / maxValue.toFloat()) * chartHeight)

            // Color del punto basado en el nivel de riesgo
            val sarBt = (reading.sarLevel + reading.bluetoothLevel)
            val pointColor = when {
                sarBt >= recommendedLimit -> android.graphics.Color.rgb(255, 50, 50) // Rojo intenso
                sarBt >= (recommendedLimit * 0.8) -> android.graphics.Color.rgb(255, 150, 50) // Naranja
                sarBt >= (recommendedLimit * 0.5) -> android.graphics.Color.rgb(255, 200, 50) // Amarillo
                else -> android.graphics.Color.rgb(100, 255, 100) // Verde
            }

            pointPaint.color = pointColor
            canvas.drawCircle(x, y, 6f, pointPaint)

            // Borde blanco para mejor visibilidad
            pointPaint.color = android.graphics.Color.WHITE
            canvas.drawCircle(x, y, 8f, pointPaint)
            pointPaint.color = pointColor
            canvas.drawCircle(x, y, 6f, pointPaint)
        }
    }

    private fun drawLabels(canvas: android.graphics.Canvas, width: Float, height: Float, padding: Float) {
        textPaint.color = android.graphics.Color.WHITE
        textPaint.textSize = 24f

        // Título
        canvas.drawText(context.getString(R.string.trends_emf_monitor_title), width / 2, padding - 20f, textPaint)

        // Etiquetas de valores
        textPaint.textSize = 18f
        textPaint.textAlign = android.graphics.Paint.Align.LEFT
        canvas.drawText("0.00", padding + 10f, height - padding + 25f, textPaint)

        textPaint.textAlign = android.graphics.Paint.Align.RIGHT
    canvas.drawText(String.format(java.util.Locale.getDefault(), "%.2f", maxValue), width - padding - 10f, padding + 25f, textPaint)

        // Etiquetas de tiempo
        if (data.size >= 2) {
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            textPaint.textAlign = android.graphics.Paint.Align.LEFT
            canvas.drawText(dateFormat.format(Date(data.first().timestamp)), padding + 10f, height - 10f, textPaint)

            textPaint.textAlign = android.graphics.Paint.Align.RIGHT
            canvas.drawText(dateFormat.format(Date(data.last().timestamp)), width - padding - 10f, height - 10f, textPaint)
        }
    }
}
