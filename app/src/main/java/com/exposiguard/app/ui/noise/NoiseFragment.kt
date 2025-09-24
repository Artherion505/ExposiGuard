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

		// Estado inicial
		binding.textNoiseLive.text = getString(R.string.noise_current_level)
		binding.textNoiseResult.text = getString(R.string.noise_result)
		binding.noiseProgress.progress = 0

		// Intentar iniciar en modo continuo si hay permiso
		startMonitoringIfPermitted(startIfOk = false)
	}

	override fun onResume() {
		super.onResume()
		startMonitoringIfPermitted()
	}

	private var continuous = false

	private fun startMonitoringIfPermitted(startIfOk: Boolean = true) {
		val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
		if (!granted) {
			binding.textNoise.text = getString(R.string.noise_permission_required)
			stopMonitoring()
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
		if (continuous) return
		val ok = noiseManager.startNoiseMonitoring()
		if (!ok) {
			binding.textNoise.text = getString(R.string.noise_monitoring_failed)
			return
		}
		continuous = true
		binding.buttonToggleContinuous.text = getString(R.string.noise_stop_monitoring)
		updateJob?.cancel()
		updateJob = viewLifecycleOwner.lifecycleScope.launch {
			while (isActive) {
				val amp = noiseManager.getCurrentNoiseLevel().coerceAtLeast(0.0)
				val idx = amplitudeToIndex(amp)
				binding.textNoiseLive.text = getString(R.string.noise_current_level_format, amp)
				binding.noiseProgress.progress = idx
				updateIndexAndColor(idx)
				pushHistory(idx.toDouble())
				binding.noiseSparkline.setData(history.toList())
				val intervalMillis = com.exposiguard.app.utils.AppEvents.getMonitoringIntervalMillis(requireContext())
				delay(intervalMillis)
			}
		}
	}

	private fun stopContinuous() {
		if (!continuous) return
		continuous = false
		binding.buttonToggleContinuous.text = getString(R.string.noise_start_monitoring)
		stopMonitoring()
		binding.noiseProgress.progress = 0
		binding.textNoiseLive.text = getString(R.string.noise_current_level)
	}

	private fun toggleContinuous() {
		if (continuous) stopContinuous() else startContinuous()
	}

	private fun runQuickSample() {
		// Toma de muestra rápida ~3s: promedio y pico
		startMonitoringIfPermitted(startIfOk = false)
		val ok = noiseManager.startNoiseMonitoring()
		if (!ok) {
			binding.textNoiseResult.text = getString(R.string.noise_measurement_failed)
			return
		}
		viewLifecycleOwner.lifecycleScope.launch {
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
			try { noiseManager.stopNoiseMonitoring() } catch (_: Exception) {}
			val avg = if (samples > 0) sum / samples else 0.0
			binding.textNoiseResult.text = getString(R.string.noise_result_format, avg, peak)
			val idx = amplitudeToIndex(avg)
			updateIndexAndColor(idx)
			pushHistory(idx.toDouble())
			binding.noiseSparkline.setData(history.toList())
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
	}

	override fun onDestroyView() {
		super.onDestroyView()
		stopMonitoring()
		_binding = null
	}
}

