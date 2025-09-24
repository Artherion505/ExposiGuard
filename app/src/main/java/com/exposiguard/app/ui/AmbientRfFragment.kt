package com.exposiguard.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.exposiguard.app.databinding.FragmentAmbientRfBinding
import com.exposiguard.app.R
import java.util.Locale

class AmbientRfFragment : Fragment() {

    private var _binding: FragmentAmbientRfBinding? = null
    private val binding get() = _binding!!

    private val prefsName = "exposiguard_settings"

    // Supuestos de referencia (urbano típico) para potencias de transmisión
    // FM/TV: usamos ERP (kW); AM: aproximamos EIRP ~ TPO (kW)
    // Referencias orientativas: FM urbana 3–10 kW ERP; TV UHF urbana 10–30 kW ERP; AM 1–10 kW TPO.
    private val ERP_FM_STRONG_KW = 10.0
    private val ERP_FM_WEAK_KW = 3.0
    private val EIRP_AM_STRONG_KW = 10.0
    private val ERP_TV_CHANNEL_KW = 25.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAmbientRfBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupEnvironmentSelector()
        setupCountDropdowns()
        loadSavedValues()
        setupListeners()
    }

    private fun setupEnvironmentSelector() {
        val items = arrayOf(
            getString(R.string.ambient_rf_env_urban),
            getString(R.string.ambient_rf_env_suburban),
            getString(R.string.ambient_rf_env_rural)
        )
        binding.inputEnvironment.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items)
        )
        binding.inputEnvironment.setOnClickListener { binding.inputEnvironment.showDropDown() }
        binding.inputEnvironment.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.inputEnvironment.showDropDown() }

        // Perfil de estimación (Conservador/Promedio/Máximo)
        val profiles = arrayOf(
            getString(R.string.ambient_rf_profile_conservative),
            getString(R.string.ambient_rf_profile_average),
            getString(R.string.ambient_rf_profile_max)
        )
        binding.inputEstimationProfile.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, profiles)
        )
        binding.inputEstimationProfile.setOnClickListener { binding.inputEstimationProfile.showDropDown() }
        binding.inputEstimationProfile.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) binding.inputEstimationProfile.showDropDown() }
    }

    private fun setupCountDropdowns() {
        // Listas con etiquetas amigables (0..50)
    fun emisorasLabel(n: Int) = resources.getQuantityString(R.plurals.ambient_rf_count_stations, n, n)
    fun canalesLabel(n: Int) = resources.getQuantityString(R.plurals.ambient_rf_count_channels, n, n)
    val emisoras = (0..50).map { emisorasLabel(it) }
    val canales = (0..50).map { canalesLabel(it) }

        fun setupDropdown(view: android.widget.AutoCompleteTextView, items: List<String>) {
            view.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, items))
            view.setOnClickListener { view.showDropDown() }
            view.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) view.showDropDown() }
        }

        setupDropdown(binding.inputFmStrong, emisoras)
        setupDropdown(binding.inputFmWeak, emisoras)
        setupDropdown(binding.inputTvOpen, emisoras)
        setupDropdown(binding.inputTvAntennaChannels, canales)
        setupDropdown(binding.inputAmStrong, emisoras)
    }

    private fun loadSavedValues() {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        fun cap50(v: Int) = v.coerceIn(0, 50)
    fun emisorasLabel(n: Int) = resources.getQuantityString(R.plurals.ambient_rf_count_stations, n, n)
    fun canalesLabel(n: Int) = resources.getQuantityString(R.plurals.ambient_rf_count_channels, n, n)
        binding.inputFmStrong.setText(emisorasLabel(cap50(prefs.getInt("ambient_rf_fm_strong", 0))), false)
        binding.inputFmWeak.setText(emisorasLabel(cap50(prefs.getInt("ambient_rf_fm_weak", 0))), false)
        binding.inputAmStrong.setText(emisorasLabel(cap50(prefs.getInt("ambient_rf_am_strong", 0))), false)
        binding.inputTvOpen.setText(emisorasLabel(cap50(prefs.getInt("ambient_rf_tv_open", 0))), false)
        val tvAntenna = prefs.getBoolean("ambient_rf_tv_antenna", false)
        binding.switchTvAntenna.isChecked = tvAntenna
        binding.layoutTvAntennaChannels.visibility = if (tvAntenna) View.VISIBLE else View.GONE
    binding.inputTvAntennaChannels.setText(canalesLabel(cap50(prefs.getInt("ambient_rf_tv_antenna_channels", 0))), false)

        val env = prefs.getString("ambient_rf_environment", getString(R.string.ambient_rf_env_suburban))
    binding.inputEnvironment.setText(env, false)

    val includeInHome = prefs.getBoolean("ambient_rf_include_in_home", false)
    binding.switchIncludeInHome.isChecked = includeInHome

    val profile = prefs.getString("ambient_rf_estimation_profile", getString(R.string.ambient_rf_profile_average))
    binding.inputEstimationProfile.setText(profile, false)

        updatePreview()
    }

    private fun setupListeners() {
        binding.switchTvAntenna.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutTvAntennaChannels.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        binding.btnSave.setOnClickListener {
            saveValues()
        }

        // Actualizar preview al cambiar campos clave
        // Al seleccionar un item en los dropdowns, actualizar preview
        val onSelect = android.widget.AdapterView.OnItemClickListener { _, _, _, _ -> updatePreview() }
        binding.inputFmStrong.setOnItemClickListener(onSelect)
        binding.inputFmWeak.setOnItemClickListener(onSelect)
        binding.inputAmStrong.setOnItemClickListener(onSelect)
        binding.inputTvOpen.setOnItemClickListener(onSelect)
        binding.inputTvAntennaChannels.setOnItemClickListener(onSelect)
        binding.inputEnvironment.setOnItemClickListener { _, _, _, _ -> updatePreview() }
        binding.inputEstimationProfile.setOnItemClickListener { _, _, _, _ -> updatePreview() }
    }

    private fun saveValues() {
        val prefs = requireContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        fun cap50i(s: String): Int {
            val m = Regex("(\\d+)").find(s)
            return (m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0).coerceIn(0, 50)
        }

        editor.putInt("ambient_rf_fm_strong", cap50i(binding.inputFmStrong.text.toString()))
        editor.putInt("ambient_rf_fm_weak", cap50i(binding.inputFmWeak.text.toString()))
        editor.putInt("ambient_rf_am_strong", cap50i(binding.inputAmStrong.text.toString()))
        editor.putInt("ambient_rf_tv_open", cap50i(binding.inputTvOpen.text.toString()))
        editor.putBoolean("ambient_rf_tv_antenna", binding.switchTvAntenna.isChecked)
    editor.putInt("ambient_rf_tv_antenna_channels", cap50i(binding.inputTvAntennaChannels.text.toString()))
        editor.putString("ambient_rf_environment", binding.inputEnvironment.text.toString())
    editor.putBoolean("ambient_rf_include_in_home", binding.switchIncludeInHome.isChecked)
    editor.putString("ambient_rf_estimation_profile", binding.inputEstimationProfile.text.toString())
        editor.apply()

        updatePreview()

        com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.saved_ok), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
        com.exposiguard.app.utils.AppEvents.emit(com.exposiguard.app.utils.AppEvents.Event.SettingsChanged)
    }

    private fun updatePreview() {
        val score = calculateAmbientIndex()
        val estimate = calculateAmbientEstimates()
        val sMilli = estimate.second * 1000.0 // mW/m²
        val sar = estimate.third
        // Mostrar índice + estimación física simplificada
    binding.textPreview.text = String.format(Locale.getDefault(), "Índice: %d/100 • S≈%.2f mW/m² • Aporte≈%.3f W/kg", score, sMilli, sar)
    }

    private fun calculateAmbientIndex(): Int {
        fun numFrom(text: String): Int { val m = Regex("(\\d+)").find(text); return (m?.value?.toIntOrNull() ?: 0).coerceIn(0,50) }
        val fmStrong = numFrom(binding.inputFmStrong.text.toString())
        val fmWeak = numFrom(binding.inputFmWeak.text.toString())
        val amStrong = numFrom(binding.inputAmStrong.text.toString())
        val tvOpen = numFrom(binding.inputTvOpen.text.toString())
        val tvAnt = if (binding.switchTvAntenna.isChecked) numFrom(binding.inputTvAntennaChannels.text.toString()) else 0

        val envText = binding.inputEnvironment.text.toString()
        val envFactor = when {
            envText.equals(getString(R.string.ambient_rf_env_urban), true) -> 1.5
            envText.equals(getString(R.string.ambient_rf_env_rural), true) -> 0.5
            else -> 1.0 // suburbano por defecto
        }

        val base = (fmStrong * 2 + fmWeak * 1 + amStrong * 1 + tvOpen * 3 + tvAnt * 2)
        val score = (base * envFactor).toInt()
        return score.coerceIn(0, 100)
    }

    /**
     * Estimación física simplificada del aporte de radio/TV ambiente
     * Devuelve Triple(index 0-100, S_total W/m², SAR_like W/kg)
     */
    private fun calculateAmbientEstimates(): Triple<Int, Double, Double> {
        fun numFrom(text: String): Int { val m = Regex("(\\d+)").find(text); return (m?.value?.toIntOrNull() ?: 0).coerceIn(0,50) }
        val fmStrong = numFrom(binding.inputFmStrong.text.toString())
        val fmWeak = numFrom(binding.inputFmWeak.text.toString())
        val amStrong = numFrom(binding.inputAmStrong.text.toString())
        val tvOpen = numFrom(binding.inputTvOpen.text.toString())
        val tvAnt = if (binding.switchTvAntenna.isChecked) numFrom(binding.inputTvAntennaChannels.text.toString()) else 0

        val envText = binding.inputEnvironment.text.toString()
        // Distancias nominales (m) según entorno
        val (rFmTv, rAm) = when {
            envText.equals(getString(R.string.ambient_rf_env_urban), true) -> 3000.0 to 5000.0
            envText.equals(getString(R.string.ambient_rf_env_rural), true) -> 10000.0 to 15000.0
            else -> 5000.0 to 8000.0 // suburbano
        }

        val nTvTotal = tvOpen + tvAnt

        // Convertir ERP a EIRP para FM/TV (EIRP≈1.64×ERP)
        val eirpFmStrongW = ERP_FM_STRONG_KW * 1000.0 * 1.64
        val eirpFmWeakW = ERP_FM_WEAK_KW * 1000.0 * 1.64
        val eirpTvW = ERP_TV_CHANNEL_KW * 1000.0 * 1.64
        val eirpAmW = EIRP_AM_STRONG_KW * 1000.0 // aproximación

        // Densidad de potencia S = EIRP / (4π r²)
        fun sAt(eirpW: Double, r: Double) = eirpW / (4.0 * Math.PI * r * r)

        val sFm = fmStrong * sAt(eirpFmStrongW, rFmTv) + fmWeak * sAt(eirpFmWeakW, rFmTv)
        val sTv = nTvTotal * sAt(eirpTvW, rFmTv)
        val sAm = amStrong * sAt(eirpAmW, rAm)
        val sTotal = sFm + sTv + sAm // W/m²

        // Perfil de estimación: factor S→SAR_like (W/kg por W/m²)
        val profileText = binding.inputEstimationProfile.text?.toString() ?: getString(R.string.ambient_rf_profile_average)
        val alpha = when (profileText) {
            getString(R.string.ambient_rf_profile_conservative) -> 0.05
            getString(R.string.ambient_rf_profile_max) -> 0.20
            else -> 0.10
        }
        val hardCap = 0.10 // W/kg límite de seguridad para evitar valores desproporcionados
        val sarLike = (alpha * sTotal).coerceAtMost(hardCap)

        val index = calculateAmbientIndex()
        return Triple(index, sTotal, sarLike)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
