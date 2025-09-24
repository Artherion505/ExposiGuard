package com.exposiguard.app.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.work.*
import com.exposiguard.app.databinding.FragmentSettingsBinding
import com.exposiguard.app.workers.EMFMonitoringWorker
import com.exposiguard.app.managers.UserProfileManager
import com.exposiguard.app.R
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var workManager: WorkManager
    private lateinit var userProfileManager: UserProfileManager
    @Inject lateinit var exposureRepository: com.exposiguard.app.repository.ExposureRepository

    companion object {
        private const val PREF_CONTINUOUS_MONITORING = "continuous_monitoring"
        private const val PREF_MONITORING_INTERVAL = "monitoring_interval"
        private const val PREF_USER_WEIGHT = "user_weight"
        private const val PREF_USER_HEIGHT = "user_height"
        private const val PREF_EXPOSURE_STANDARD = "exposure_standard"
        private const val PREF_UNITS_SYSTEM = "units_system"
        private const val PREF_AVERAGING_WINDOW_MIN = "averaging_window_min"
        private const val PREF_MONITOR_HOME = "monitor_home"
        private const val PREF_MONITOR_WIFI = "monitor_wifi"
        private const val PREF_MONITOR_EMF = "monitor_emf"
        private const val PREF_MONITOR_NOISE = "monitor_noise"
        // Eliminado: ya no se permite alternar modo claro para evitar parpadeos
        private const val EMF_MONITORING_WORK_TAG = "emf_monitoring_work"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        android.util.Log.d("SettingsFragment", "onViewCreated: Iniciando configuración")

        sharedPreferences = requireContext().getSharedPreferences("exposiguard_settings", Context.MODE_PRIVATE)
        workManager = WorkManager.getInstance(requireContext())
        userProfileManager = UserProfileManager(requireContext())

        android.util.Log.d("SettingsFragment", "onViewCreated: Configurando selectores")
        setupIntervalSelector()
    setupAveragingWindowSelector()
        setupStandardSelector()
        setupLanguageSelector()
        loadSettings()
        setupListeners()

        android.util.Log.d("SettingsFragment", "onViewCreated: Configuración completada")
    }

    private fun setupIntervalSelector() {
        val intervals = arrayOf("1", "5", "10", "15", "30", "60")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, intervals)
        binding.intervalInput.setAdapter(adapter)
    }

    private fun setupAveragingWindowSelector() {
        val windows = arrayOf("10", "30", "60", "120", "360", "720")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, windows)
        binding.averagingWindowInput.setAdapter(adapter)
    }

    private fun setupStandardSelector() {
        val standards = arrayOf("FCC (USA)", "ICNIRP (Europa)")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, standards)
        binding.standardInput.setAdapter(adapter)
    }

    private fun setupLanguageSelector() {
        val languages = arrayOf(getString(R.string.language_spanish), getString(R.string.language_english))
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, languages)
        binding.languageInput.setAdapter(adapter)
    }

    private fun loadSettings() {
        val continuousMonitoring = sharedPreferences.getBoolean(PREF_CONTINUOUS_MONITORING, false)
        val interval = sharedPreferences.getString(PREF_MONITORING_INTERVAL, "5") ?: "5"
    val averagingWindow = sharedPreferences.getString(PREF_AVERAGING_WINDOW_MIN, null)
        val weight = userProfileManager.getWeightInCurrentUnits().toString()
        val height = userProfileManager.getHeightInCurrentUnits().toString()
        val standard = sharedPreferences.getString(PREF_EXPOSURE_STANDARD, "FCC (USA)") ?: "FCC (USA)"
        val isDebuggable = try {
            (requireContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) { false }
        val monitorHome = sharedPreferences.getBoolean(PREF_MONITOR_HOME, true)
        val monitorWifi = sharedPreferences.getBoolean(PREF_MONITOR_WIFI, true)
        val monitorEmf = sharedPreferences.getBoolean(PREF_MONITOR_EMF, true)
        val monitorNoise = sharedPreferences.getBoolean(PREF_MONITOR_NOISE, true)

        binding.continuousMonitoringSwitch.isChecked = continuousMonitoring
        binding.intervalInput.setText(interval, false)
        // Si no hay preferencia específica de ventana, derivaremos desde el intervalo
        val windowToShow = averagingWindow ?: when (interval) {
            "1" -> "10"
            "5" -> "30"
            "10" -> "60"
            "30" -> "360"
            "60" -> "720"
            else -> getString(R.string.settings_averaging_window_value)
        }
        binding.averagingWindowInput.setText(windowToShow, false)
        binding.weightInput.setText(weight)
        binding.heightInput.setText(height)
        binding.standardInput.setText(standard, false)
        // Controles experimentales eliminados
        binding.monitorHome.isChecked = monitorHome
        binding.monitorWifi.isChecked = monitorWifi
        binding.monitorEmf.isChecked = monitorEmf
        binding.monitorNoise.isChecked = monitorNoise

        // Mostrar controles de depuración solo en builds debuggable
        // Botón de backfill y controles de debug eliminados

        // Cargar idioma actual usando los mismos strings que el selector
        val currentLanguageCode = com.exposiguard.app.utils.LocaleHelper.getPersistedLanguage(requireContext())
        val currentLanguageDisplayName = if (currentLanguageCode == "es") {
            getString(R.string.language_spanish)
        } else {
            getString(R.string.language_english)
        }
        binding.languageInput.setText(currentLanguageDisplayName, false)

        // Cargar IMC guardado
        loadSavedIMC()

        // Actualizar hints de unidades
        updateUnitsHints()

        android.util.Log.d("SettingsFragment", "loadSettings: Configuración cargada")
    }

    private fun updateUnitsHints() {
        val weightHint = getString(R.string.weight_label) + " (" + getString(R.string.units_weight_kg) + ")"
        val heightHint = getString(R.string.height_label) + " (" + getString(R.string.units_height_cm) + ")"

        binding.weightInputLayout.hint = weightHint
        binding.heightInputLayout.hint = heightHint
    }

    private fun setupListeners() {
        binding.continuousMonitoringSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_CONTINUOUS_MONITORING, isChecked).apply()
            binding.intervalInputLayout.isEnabled = isChecked

            if (isChecked) {
                startContinuousMonitoring()
            } else {
                stopContinuousMonitoring()
            }
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
        }

        binding.intervalInput.setOnItemClickListener { _, _, _, _ ->
            val interval = binding.intervalInput.text.toString()
            sharedPreferences.edit().putString(PREF_MONITORING_INTERVAL, interval).apply()

            // Reiniciar el monitoreo con el nuevo intervalo si está activo
            if (binding.continuousMonitoringSwitch.isChecked) {
                startContinuousMonitoring()
            }
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
        }

        binding.standardInput.setOnItemClickListener { _, _, _, _ ->
            val standard = binding.standardInput.text.toString()
            sharedPreferences.edit().putString(PREF_EXPOSURE_STANDARD, standard).apply()
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
        }

        binding.languageInput.setOnItemClickListener { _, _, _, _ ->
            val selectedLanguage = binding.languageInput.text.toString()
            val languageCode = if (selectedLanguage == getString(R.string.language_spanish)) {
                "es"
            } else {
                "en"
            }

            // Aplicar cambio de idioma inmediatamente sin diálogo de confirmación
            com.exposiguard.app.utils.LocaleHelper.applyLanguageChange(requireActivity(), languageCode)
        }

        // Controles experimentales eliminados
        // Cambios en la ventana de promediado
        binding.averagingWindowInput.setOnItemClickListener { _, _, _, _ ->
            val window = binding.averagingWindowInput.text.toString()
            sharedPreferences.edit().putString(PREF_AVERAGING_WINDOW_MIN, window).apply()
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
        }

        

        // Checkboxes de pestañas a monitorear
        binding.monitorHome.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_MONITOR_HOME, isChecked).apply()
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
        }
        binding.monitorWifi.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_MONITOR_WIFI, isChecked).apply()
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
        }
        binding.monitorEmf.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_MONITOR_EMF, isChecked).apply()
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
        }
        binding.monitorNoise.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_MONITOR_NOISE, isChecked).apply()
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
        }

    // Switch de modo claro eliminado

        // Configurar botón de guardar perfil
        binding.saveProfileButton.setOnClickListener {
            saveUserProfile()
        }

        // Configurar botón de calcular IMC
        binding.calculateImcButton.setOnClickListener {
            calculateAndDisplayIMC()
        }

        // Configurar listeners para recalcular IMC automáticamente
        binding.weightInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (_binding != null && isAdded && context != null) {
                    calculateAndDisplayIMC()
                }
            }
        })

        binding.heightInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (_binding != null && isAdded && context != null) {
                    calculateAndDisplayIMC()
                }
            }
        })

        // Botón de backfill eliminado
    }

    private fun startContinuousMonitoring() {
        val intervalMinutes = binding.intervalInput.text.toString().toLongOrNull() ?: 5L

        val workRequest = PeriodicWorkRequestBuilder<EMFMonitoringWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true) // Solo cuando batería no esté baja
                    .build()
            )
            .addTag(EMF_MONITORING_WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            EMF_MONITORING_WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    private fun stopContinuousMonitoring() {
        workManager.cancelAllWorkByTag(EMF_MONITORING_WORK_TAG)
    }

    private fun saveUserProfile() {
        // Verificar que el fragmento esté en un estado válido
        if (_binding == null || !isAdded || context == null) {
            android.util.Log.w("SettingsFragment", "saveUserProfile: Fragmento no está listo")
            return
        }

        val weightText = binding.weightInput.text.toString()
        val heightText = binding.heightInput.text.toString()

        try {
            val weight = weightText.toDoubleOrNull()
            val height = heightText.toDoubleOrNull()

            if (weight == null || weight <= 0) {
                val units = getString(R.string.units_weight_kg)
                binding.weightInput.error = getString(R.string.error_weight_invalid, units)
                return
            }

            if (height == null || height <= 0) {
                val units = getString(R.string.units_height_cm)
                binding.heightInput.error = getString(R.string.error_height_invalid, units)
                return
            }

            // Validar rangos razonables en unidades métricas
            val minWeight = 30.0
            val maxWeight = 300.0
            val weightUnits = getString(R.string.units_weight_kg)

            if (weight < minWeight || weight > maxWeight) {
                binding.weightInput.error = getString(R.string.error_weight_range, "$minWeight-$maxWeight $weightUnits")
                return
            }

            val minHeight = 100.0
            val maxHeight = 250.0
            val heightUnits = getString(R.string.units_height_cm)

            if (height < minHeight || height > maxHeight) {
                binding.heightInput.error = getString(R.string.error_height_range, "$minHeight-$maxHeight $heightUnits")
                return
            }

            // Guardar en SharedPreferences usando las funciones de conversión
            userProfileManager.saveWeightInCurrentUnits(weight)
            userProfileManager.saveHeightInCurrentUnits(height)

            // Calcular y guardar IMC
            val heightM = height / 100.0
            val imc = weight / (heightM * heightM)
            sharedPreferences.edit().putFloat("user_imc", imc.toFloat()).apply()

            // También guardar el estándar de exposición actual
            val standard = binding.standardInput.text.toString()
            sharedPreferences.edit().putString(PREF_EXPOSURE_STANDARD, standard).apply()

            // getString(R.string.comment_clear_errors)
            binding.weightInput.error = null
            binding.heightInput.error = null

            // Mostrar confirmación
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.profile_saved_successfully),
                android.widget.Toast.LENGTH_SHORT
            ).show()

            // Notificar cambios de settings
            com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)

        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "saveUserProfile: " + getString(R.string.comment_error_saving_profile), e)
            android.widget.Toast.makeText(
                requireContext(),
                getString(R.string.comment_error_saving_profile_message),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun loadSavedIMC() {
        val savedImc = sharedPreferences.getFloat("user_imc", -1.0f)
        if (savedImc > 0) {
            // Determinar categoría del IMC guardado
            val category = when {
                savedImc < 18.5 -> getString(R.string.imc_category_underweight)
                savedImc < 25.0 -> getString(R.string.imc_category_normal)
                savedImc < 30.0 -> getString(R.string.imc_category_overweight)
                else -> getString(R.string.imc_category_obese)
            }

            // Mostrar IMC guardado
            binding.imcInfo.text = getString(R.string.settings_bmi_info, savedImc, category)

            // Cambiar color según categoría
            val colorRes = when {
                savedImc < 18.5 -> com.exposiguard.app.R.color.imc_low
                savedImc < 25.0 -> com.exposiguard.app.R.color.imc_normal
                savedImc < 30.0 -> com.exposiguard.app.R.color.imc_high
                else -> com.exposiguard.app.R.color.imc_obese
            }
            binding.imcInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), colorRes))

            android.util.Log.d("SettingsFragment", "loadSavedIMC: IMC cargado desde SharedPreferences: $savedImc")
        } else {
            // No hay IMC guardado, mostrar mensaje por defecto
            binding.imcInfo.text = getString(R.string.settings_bmi_info)
            binding.imcInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.exposiguard.app.R.color.imc_default))
            android.util.Log.d("SettingsFragment", "loadSavedIMC: No hay IMC guardado")
        }
    }

    private fun calculateAndDisplayIMC() {
        android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: Iniciando cálculo de IMC")

        // Verificar que el binding esté disponible y el fragmento esté adjunto
        if (_binding == null || !isAdded || context == null) {
            android.util.Log.w("SettingsFragment", "calculateAndDisplayIMC: Fragmento no está listo, saliendo")
            return
        }

        val weightText = binding.weightInput.text.toString()
        val heightText = binding.heightInput.text.toString()

        android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: Valores - Peso: $weightText, Altura: $heightText")

        try {
            val weight = weightText.toDoubleOrNull()
            val height = heightText.toDoubleOrNull()

            android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: Valores parseados - Peso: $weight, Altura: $height")

            if (weight != null && height != null && weight > 0 && height > 0) {
                // Convertir altura a metros
                val heightM = height / 100.0

                // Calcular IMC
                val imc = weight / (heightM * heightM)

                android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: IMC calculado: $imc")

                // Guardar IMC en SharedPreferences
                sharedPreferences.edit().putFloat("user_imc", imc.toFloat()).apply()
                android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: IMC guardado en SharedPreferences: $imc")

                // Determinar categoría
                val category = when {
                    imc < 18.5 -> getString(R.string.imc_category_underweight)
                    imc < 25.0 -> getString(R.string.imc_category_normal)
                    imc < 30.0 -> getString(R.string.imc_category_overweight)
                    else -> getString(R.string.imc_category_obese)
                }

                // Mostrar resultado
                val resultText = getString(R.string.settings_bmi_info, imc, category)
                android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: Texto a mostrar: $resultText")
                binding.imcInfo.text = resultText

                // Cambiar color según categoría
                val colorRes = when {
                    imc < 18.5 -> com.exposiguard.app.R.color.imc_low
                    imc < 25.0 -> com.exposiguard.app.R.color.imc_normal
                    imc < 30.0 -> com.exposiguard.app.R.color.imc_high
                    else -> com.exposiguard.app.R.color.imc_obese
                }
                val color = androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)
                android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: Color aplicado: $colorRes")
                binding.imcInfo.setTextColor(color)

                android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: IMC mostrado correctamente")
            } else {
                val defaultText = getString(R.string.settings_bmi_info)
                android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: Valores inválidos, mostrando texto por defecto: $defaultText")
                binding.imcInfo.text = defaultText
                binding.imcInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.exposiguard.app.R.color.imc_default))

                android.util.Log.d("SettingsFragment", "calculateAndDisplayIMC: Valores inválidos")
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsFragment", "calculateAndDisplayIMC: " + getString(R.string.comment_error_calculating_bmi), e)
            if (_binding != null && isAdded) {
                binding.imcInfo.text = getString(R.string.settings_bmi_error)
                binding.imcInfo.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.exposiguard.app.R.color.imc_obese))
            }
        }
    }

    private fun restartApplication() {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
        intent?.let {
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(it)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Modo claro eliminado: no se cambia el modo del tema desde Settings
}
