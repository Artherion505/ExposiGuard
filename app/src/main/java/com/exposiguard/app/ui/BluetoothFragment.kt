package com.exposiguard.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.exposiguard.app.databinding.FragmentBluetoothBinding
import com.exposiguard.app.managers.BluetoothManager
import com.exposiguard.app.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BluetoothFragment : Fragment() {

    private var _binding: FragmentBluetoothBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var bluetoothManager: BluetoothManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBluetoothBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeData()

        // Escuchar eventos de app para refrescar indicadores
        com.exposiguard.app.utils.AppEvents.events
            .onEach {
                when (it) {
                    is com.exposiguard.app.utils.AppEvents.Event.SettingsChanged,
                    is com.exposiguard.app.utils.AppEvents.Event.DataChanged -> {
                        try { com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.bluetooth_updated), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show() } catch (_: Exception) {}
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupUI() {
        binding.apply {
            chipBtStatus.text = if (bluetoothManager.isBluetoothEnabled()) getString(R.string.bluetooth_status_on) else getString(R.string.bluetooth_status_off)
            deviceCountText.text = getString(R.string.bluetooth_devices_found)
            scanButton.setOnClickListener { bluetoothManager.startBluetoothScan() }
            usageAccessButton.setOnClickListener {
                bluetoothManager.openUsageAccessSettings()
                // Mostrar mensaje
                try { com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.bluetooth_grant_access_message), com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show() } catch (_: Exception) {}
            }
            // Botón de settings eliminado del layout
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            bluetoothManager.bluetoothDevices.collect { devices ->
                binding.apply {
                    deviceCountText.text = getString(R.string.bluetooth_devices_count, devices.size)
                    emptyStateText.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            bluetoothManager.bluetoothExposure.collect { exposure ->
                val formatted = String.format(java.util.Locale.getDefault(), "%.3f W/kg", exposure)
                binding.exposureValue.text = formatted
                // chip de exposición breve
                try { binding.chipBtExposure.text = formatted } catch (_: Exception) {}
                binding.exposureBar.progress = (exposure * 100).toInt().coerceIn(0, 100)
                // Asegurar un progreso mínimo visible para que no parezca insignificante
                val rawProgress = (exposure * 100).toInt().coerceIn(0, 100)
                val displayProgress = when {
                    exposure == 0.0 -> 0 // 0% si realmente no hay exposición
                    rawProgress == 0 && exposure > 0.0 -> 3 // Mínimo 3% si hay exposición pero cálculo da 0
                    rawProgress in 1..4 -> 5 // Si está entre 1-4%, mostrar mínimo 5%
                    else -> rawProgress // Mantener valor original para valores normales
                }
                binding.exposureBar.progress = displayProgress
            }
        }

        // Observa absorción en mano hoy
        viewLifecycleOwner.lifecycleScope.launch {
            bluetoothManager.handAbsorptionToday.collect { dose ->
                binding.handAbsorptionText.text = String.format(java.util.Locale.getDefault(), "%.2f J/kg", dose)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Al volver de Settings, intentar actualizar absorción y mostrar/ocultar botón
        bluetoothManager.updateHandAbsorption()
    val hasAccess = bluetoothManager.hasUsageAccess()
    binding.usageAccessButton.visibility = if (hasAccess) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
