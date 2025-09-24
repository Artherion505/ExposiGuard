package com.exposiguard.app.ui.noise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.exposiguard.app.databinding.FragmentNoiseBinding
import com.exposiguard.app.managers.NoiseManager
import com.exposiguard.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NoiseFragment : Fragment() {

	private var _binding: FragmentNoiseBinding? = null
	private val binding get() = _binding!!

	private lateinit var noiseManager: NoiseManager
	private var updateJob: Job? = null
	private val history = ArrayDeque<Double>()
	private val maxHistory = 120 // ~1 min si 500ms por muestra

	// EMF Detector variables
	private var emfDetectionJob: Job? = null
	private var emfActive = false
	private val emfBars = mutableListOf<View>()
	private var emfSensitivity = 50 // 0-100

	// Launcher para permisos de audio
	private val requestAudioPermissionLauncher = registerForActivityResult(
		androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
	) { isGranted ->
		if (isGranted) {
			// Permiso concedido, intentar iniciar el monitoreo
			binding.textNoise.text = getString(R.string.noise_monitor_title)
			// Reintentar la acción que falló
			retryPendingAction()
		} else {
			// Permiso denegado
			binding.textNoise.text = getString(R.string.noise_permission_required)
		}
	}

	private var pendingAction: (() -> Unit)? = null

	private fun retryPendingAction() {
		pendingAction?.invoke()
		pendingAction = null
	}

	private fun requestAudioPermission(action: () -> Unit) {
		pendingAction = action
		requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FragmentNoiseBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		noiseManager = NoiseManager(requireContext())

		// Configurar acciones de botones
		binding.buttonQuickSample.setOnClickListener { runQuickSample() }
		binding.buttonToggleContinuous.setOnClickListener { toggleContinuous() }

		// Configurar EMF Detector
		setupEmfDetector()

		// Estado inicial
		binding.textNoiseLive.text = getString(R.string.noise_current_level)
		binding.textNoiseResult.text = getString(R.string.noise_result)
		binding.noiseProgress.progress = 0

		// Intentar iniciar en modo continuo si hay permiso
		startMonitoringIfPermitted(startIfOk = false)
	}

	private fun setupEmfDetector() {
		// Configurar barras del EMF detector
		emfBars.addAll(listOf(
			binding.emfBar1, binding.emfBar2, binding.emfBar3, binding.emfBar4, binding.emfBar5,
			binding.emfBar6, binding.emfBar7, binding.emfBar8, binding.emfBar9, binding.emfBar10
		))

		// Configurar SeekBar de sensibilidad
		binding.emfSensitivitySeekbar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
				emfSensitivity = progress
				updateEmfSensitivityText()
			}
			override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
			override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
		})

		// Configurar botones del EMF detector
		binding.buttonEmfStart.setOnClickListener { startEmfDetection() }
		binding.buttonEmfStop.setOnClickListener { stopEmfDetection() }

		// Estado inicial
		updateEmfSensitivityText()
		binding.textEmfStatus.text = getString(R.string.emf_detector_idle)
		binding.textEmfValue.text = "0.0"
	}

	private fun updateEmfSensitivityText() {
		val sensitivityText = when {
			emfSensitivity < 33 -> getString(R.string.emf_sensitivity_low)
			emfSensitivity < 66 -> getString(R.string.emf_sensitivity_medium)
			else -> getString(R.string.emf_sensitivity_high)
		}
		binding.textEmfSensitivity.text = sensitivityText
	}

	private fun startEmfDetection() {
		if (emfActive) return

		emfActive = true
		binding.textEmfStatus.text = getString(R.string.emf_detector_active)
		binding.buttonEmfStart.isEnabled = false
		binding.buttonEmfStop.isEnabled = true

		emfDetectionJob = viewLifecycleOwner.lifecycleScope.launch {
			while (isActive && emfActive) {
				val emfValue = generateEmfReading()
				updateEmfDisplay(emfValue)
				delay(200) // Actualizar cada 200ms para efecto visual
			}
		}
	}

	private fun stopEmfDetection() {
		emfActive = false
		emfDetectionJob?.cancel()
		binding.textEmfStatus.text = getString(R.string.emf_detector_idle)
		binding.buttonEmfStart.isEnabled = true
		binding.buttonEmfStop.isEnabled = false
		binding.textEmfValue.text = "0.0"
		resetEmfBars()
	}

	private fun generateEmfReading(): Double {
		// Simular lecturas EMF realistas con algo de ruido
		val baseValue = when {
			emfSensitivity < 33 -> 0.1 + kotlin.random.Random.nextDouble(0.0, 0.5) // Baja sensibilidad
			emfSensitivity < 66 -> 0.2 + kotlin.random.Random.nextDouble(0.0, 2.0) // Media sensibilidad
			else -> 0.5 + kotlin.random.Random.nextDouble(0.0, 5.0) // Alta sensibilidad
		}

		// Añadir algo de variación para simular lecturas reales
		return baseValue + (kotlin.random.Random.nextDouble(-0.2, 0.2) * baseValue)
	}

	private fun updateEmfDisplay(value: Double) {
		// Actualizar valor mostrado
		binding.textEmfValue.text = getString(R.string.emf_value_format, value)

		// Actualizar barras del detector
		updateEmfBars(value)

		// Cambiar color del texto si hay EMF alto
		val textColor = if (value > 2.0) android.graphics.Color.RED else android.graphics.Color.WHITE
		binding.textEmfValue.setTextColor(textColor)

		// Actualizar status si hay EMF alto
		if (value > 3.0) {
			binding.textEmfStatus.text = getString(R.string.emf_detector_high_emf)
		} else {
			binding.textEmfStatus.text = getString(R.string.emf_detector_active)
		}
	}

	private fun updateEmfBars(value: Double) {
		// Calcular cuántas barras encender basado en el valor
		val maxBars = 10
		val barsToLight = when {
			value < 0.1 -> 0
			value < 0.5 -> 1
			value < 1.0 -> 2
			value < 1.5 -> 3
			value < 2.0 -> 4
			value < 2.5 -> 5
			value < 3.0 -> 6
			value < 3.5 -> 7
			value < 4.0 -> 8
			value < 4.5 -> 9
			else -> 10
		}

		// Actualizar cada barra
		emfBars.forEachIndexed { index, bar ->
			val isOn = index < barsToLight
			val drawableRes = if (isOn) R.drawable.emf_bar_on else R.drawable.emf_bar_off
			bar.background = ContextCompat.getDrawable(requireContext(), drawableRes)
		}
	}

	private fun resetEmfBars() {
		emfBars.forEach { bar ->
			bar.background = ContextCompat.getDrawable(requireContext(), R.drawable.emf_bar_off)
		}
	}

	override fun onResume() {
		super.onResume()
		// No iniciar automáticamente el monitoreo continuo al reanudar
		// Solo verificar permisos y mostrar mensaje si es necesario
		val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
		if (!granted) {
			binding.textNoise.text = getString(R.string.noise_permission_required)
		}
	}

	private var continuous = false

	private fun startMonitoringIfPermitted(startIfOk: Boolean = true) {
		val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
		if (!granted) {
			// Solicitar permiso en lugar de solo mostrar mensaje
			requestAudioPermission {
				if (startIfOk) startContinuous()
			}
			return
		}

		if (startIfOk) startContinuous()
	}

	private fun stopMonitoring() {
		updateJob?.cancel()
		updateJob = null
		try { noiseManager.stopNoiseMonitoring() } catch (_: Exception) {}
	}

	private fun startContinuous() {
		if (continuous) {
			android.util.Log.d("NoiseFragment", "Continuous monitoring already active, skipping")
			return
		}

		android.util.Log.d("NoiseFragment", "Starting continuous noise monitoring...")
		val ok = noiseManager.startNoiseMonitoring()
		if (!ok) {
			android.util.Log.e("NoiseFragment", "Failed to start noise monitoring in startContinuous")
			binding.textNoise.text = getString(R.string.noise_monitoring_failed)
			return
		}

		android.util.Log.d("NoiseFragment", "Continuous monitoring started successfully")
		continuous = true
		binding.buttonToggleContinuous.text = getString(R.string.noise_stop_monitoring)
		updateJob?.cancel()

		// Usar el mismo enfoque que Quick Measurement pero continuo
		updateJob = viewLifecycleOwner.lifecycleScope.launch {
			val startTime = System.currentTimeMillis()
			while (isActive && continuous) {
				try {
					// Tomar una "muestra rápida" cada intervalo
					val sampleStart = System.currentTimeMillis()
					var sampleSum = 0.0
					var sampleCount = 0
					val sampleDuration = 500 // 500ms por muestra, como Quick Measurement

					// Recopilar datos durante 500ms
					while (System.currentTimeMillis() - sampleStart < sampleDuration && isActive && continuous) {
						val v = noiseManager.getCurrentNoiseLevel().coerceAtLeast(0.0)
						sampleSum += v
						sampleCount++
						delay(50) // Pequeño delay para no sobrecargar
					}

					if (sampleCount > 0) {
						val avgAmplitude = sampleSum / sampleCount
						val idx = amplitudeToIndex(avgAmplitude)

						// Actualizar interfaz
						binding.textNoiseLive.text = getString(R.string.noise_current_level_format, avgAmplitude)
						binding.noiseProgress.progress = idx
						updateIndexAndColor(idx)
						pushHistory(idx.toDouble())
						binding.noiseSparkline.setData(history.toList())

						android.util.Log.d("NoiseFragment", "Continuous sample - Avg: $avgAmplitude, Index: $idx, Samples: $sampleCount")
					}

					// Timeout de seguridad
					if (System.currentTimeMillis() - startTime > 5 * 60 * 1000) {
						android.util.Log.d("NoiseFragment", "Continuous monitoring timeout reached, stopping automatically")
						stopContinuous()
						break
					}

				} catch (e: Exception) {
					android.util.Log.e("NoiseFragment", "Error in continuous monitoring loop: ${e.message}", e)
					break
				}
			}
		}
	}

	private fun stopContinuous() {
		if (!continuous) return
		continuous = false
		binding.buttonToggleContinuous.text = getString(R.string.noise_start_monitoring)
		stopMonitoring()
		// Resetear completamente la interfaz
		binding.noiseProgress.progress = 0
		binding.textNoiseLive.text = getString(R.string.noise_current_level)
		binding.textNoiseIndex.text = ""
		// Resetear color del progress bar
		binding.noiseProgress.progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN)
	}

	private fun toggleContinuous() {
		val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
		if (!granted) {
			// Solicitar permiso antes de intentar toggle
			requestAudioPermission { toggleContinuous() }
			return
		}

		if (continuous) stopContinuous() else startContinuous()
	}

	private fun runQuickSample() {
		android.util.Log.d("NoiseFragment", "Starting quick sample measurement")

		val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
		android.util.Log.d("NoiseFragment", "Audio permission granted: $granted")

		if (!granted) {
			// Solicitar permiso antes de intentar la medición rápida
			android.util.Log.d("NoiseFragment", "Requesting audio permission for quick sample")
			requestAudioPermission { runQuickSample() }
			return
		}

		// Guardar estado del monitoreo continuo
		val wasContinuous = continuous
		android.util.Log.d("NoiseFragment", "Continuous monitoring was active: $wasContinuous")

		// Detener monitoreo continuo si está activo para evitar conflictos
		if (continuous) {
			android.util.Log.d("NoiseFragment", "Stopping continuous monitoring for quick sample")
			stopContinuous()
			// Pequeña pausa para asegurar que se liberen los recursos
			Thread.sleep(100)
		}

		// Toma de muestra rápida ~3s: promedio y pico
		android.util.Log.d("NoiseFragment", "Starting MediaRecorder for quick sample")
		val ok = noiseManager.startNoiseMonitoring()
		if (!ok) {
			android.util.Log.e("NoiseFragment", "Failed to start MediaRecorder for quick sample")
			binding.textNoiseResult.text = getString(R.string.noise_measurement_failed)
			// Reiniciar monitoreo continuo si estaba activo
			if (wasContinuous) {
				startContinuous()
			}
			return
		}

		android.util.Log.d("NoiseFragment", "Quick sample measurement started successfully")
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val start = System.currentTimeMillis()
				var samples = 0
				var sum = 0.0
				var peak = 0.0
				while (System.currentTimeMillis() - start < 3000) {
					val v = noiseManager.getCurrentNoiseLevel().coerceAtLeast(0.0)
					sum += v
					samples++
					if (v > peak) peak = v
					val idx = amplitudeToIndex(v)
					binding.noiseProgress.progress = idx
					delay(100)
				}

				// Calcular y mostrar resultados
				try { noiseManager.stopNoiseMonitoring() } catch (_: Exception) {}
				val avg = if (samples > 0) sum / samples else 0.0
				val resultText = getString(R.string.noise_result_format, avg, peak)
				android.util.Log.d("NoiseFragment", "Quick sample completed: avg=$avg, peak=$peak, result=$resultText")
				binding.textNoiseResult.text = resultText
				val idx = amplitudeToIndex(avg)
				updateIndexAndColor(idx)
				pushHistory(idx.toDouble())
				binding.noiseSparkline.setData(history.toList())

			} finally {
				// Reiniciar monitoreo continuo si estaba activo originalmente
				if (wasContinuous && !continuous) {
					// Pequeña pausa antes de reiniciar
					delay(200)
					startContinuous()
				}
			}
		}
	}

	private fun amplitudeToIndex(amp: Double): Int {
		// Normaliza por un valor típico de maxAmplitude (~32767 en muchos equipos)
		val rawIndex = (amp / 32767.0 * 100.0).coerceIn(0.0, 100.0).toInt()

		// Asegurar un progreso mínimo visible para que no parezca insignificante
		return when {
			rawIndex == 0 && amp > 0.0 -> 3 // Mínimo 3% si hay amplitud pero índice es 0
			rawIndex in 1..4 -> 5 // Si está entre 1-4%, mostrar mínimo 5%
			else -> rawIndex // Mantener valor original para valores normales
		}
	}

	private fun updateIndexAndColor(index: Int) {
		// Semáforo simple: <30 bajo, 30-70 moderado, 70-85 alto, >85 crítico
		val (label, color) = when {
			index >= 85 -> getString(R.string.noise_level_critical) to android.graphics.Color.RED
			index >= 70 -> getString(R.string.noise_level_high) to android.graphics.Color.rgb(255, 165, 0) // naranja
			index >= 30 -> getString(R.string.noise_level_moderate) to android.graphics.Color.YELLOW
			else -> getString(R.string.noise_level_low) to android.graphics.Color.GREEN
		}
		binding.textNoiseIndex.text = getString(R.string.noise_index_format, index, label)
		// Tint del progress
		try { binding.noiseProgress.setIndicatorColor(color) } catch (_: Exception) {}
	}

	private fun pushHistory(v: Double) {
		history.addLast(v)
		while (history.size > maxHistory) history.removeFirst()
	}

	override fun onPause() {
		super.onPause()
		stopMonitoring()
		stopEmfDetection()
	}

	override fun onDestroyView() {
		super.onDestroyView()
		stopMonitoring()
		stopEmfDetection()
		_binding = null
	}
}

