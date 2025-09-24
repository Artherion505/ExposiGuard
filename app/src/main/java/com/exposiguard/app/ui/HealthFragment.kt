package com.exposiguard.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.exposiguard.app.databinding.FragmentHealthBinding
import com.exposiguard.app.managers.HealthManager
import com.exposiguard.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class HealthFragment : Fragment() {

	private var _binding: FragmentHealthBinding? = null
	private val binding get() = _binding!!

	private lateinit var healthManager: HealthManager

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		_binding = FragmentHealthBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		healthManager = HealthManager(requireContext())

		// Ocultar botón de refrescar: la pantalla se auto-actualiza
		binding.refreshHealthButton.visibility = View.GONE

		setupButtons()
		loadHealthData()

		// Suscribirse a eventos de la app para refrescar automáticamente
		com.exposiguard.app.utils.AppEvents.events
			.onEach {
				when (it) {
					is com.exposiguard.app.utils.AppEvents.Event.DataChanged,
					is com.exposiguard.app.utils.AppEvents.Event.SettingsChanged -> loadHealthData()
				}
			}
			.launchIn(viewLifecycleOwner.lifecycleScope)
	}

	override fun onResume() {
		super.onResume()
		// Re-cargar datos al volver a la vista
		loadHealthData()
	}

	private fun setupButtons() {
		binding.apply {
			// Health Connect (placeholder por ahora)
			healthConnectButton.isEnabled = healthManager.isHealthConnectAvailable()
			healthConnectButton.setOnClickListener {
				// En futuras versiones, lanzar flujo de permisos/connection de Health Connect
				try { com.google.android.material.snackbar.Snackbar.make(root, "Health Connect no disponible", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() } catch (_: Exception) {}
			}

			connectSmartwatchButton.setOnClickListener {
				val connected = healthManager.connectToSmartwatch()
				updateHealthStatus("Smartwatch ${if (connected) "conectado" else "no conectado"}")
				loadHealthData()
			}
			syncSmartwatchButton.setOnClickListener {
				viewLifecycleOwner.lifecycleScope.launch {
					val ok = healthManager.syncWithSmartwatch()
					updateHealthStatus(if (ok) "Sincronización completada" else "No se pudo sincronizar")
					loadHealthData()
				}
			}
		}
	}

	private fun updateHealthStatus(msg: String) {
		try { com.google.android.material.snackbar.Snackbar.make(binding.root, msg, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() } catch (_: Exception) {}
	}

	private fun loadHealthData() {
		// Mostrar estado/ayuda inicial
		binding.healthDataText.text = getString(R.string.health_loading_data)

		// Verificar permisos de micrófono/actividad si fueran necesarios (no estrictos ahora)
		val audioGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
		val activityGranted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

		viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
			val heart = healthManager.getHeartRate()
			val steps = healthManager.getStepCount()
			val sleep = healthManager.getSleepHours()

			val connected = healthManager.isSmartwatchConnected()
			val lastSync = healthManager.getLastSyncTime()
			val lastSyncStr = if (lastSync > 0) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSync)) else "—"

			val text = buildString {
				append("🩺 Salud y bienestar\n\n")
				append("• Ritmo cardiaco: ${if (heart > 0) String.format(java.util.Locale.getDefault(), "%.0f bpm", heart) else "--"}\n")
				append("• Pasos hoy: ${if (steps > 0) steps else "--"}\n")
				append("• Sueño: ${if (sleep > 0) String.format(java.util.Locale.getDefault(), "%.1f h", sleep) else "--"}\n\n")
				append("⌚ Smartwatch: ${if (connected) "conectado" else "desconectado"}\n")
				append("• Última sincronización: $lastSyncStr\n\n")
				append("Permisos: \n")
				append("• Audio: ${if (audioGranted) "✅" else "⚠️"}  • Actividad: ${if (activityGranted) "✅" else "⚠️"}")
			}

			withContext(Dispatchers.Main) {
				if (isAdded && _binding != null) {
					binding.healthDataText.text = text
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}
}

