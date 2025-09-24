package com.exposiguard.app.utils

import android.content.Context
import com.exposiguard.app.R

object AmbientRfEstimator {

    data class Estimate(
        val index: Int,           // 0–100
        val sTotalWPerM2: Double, // W/m²
        val sarLikeWPerKg: Double, // W/kg
        val sFmWPerM2: Double = 0.0,
        val sTvWPerM2: Double = 0.0,
        val sAmWPerM2: Double = 0.0,
        val sarBroadcastWPerKg: Double = 0.0
    )

    // Potencias típicas (kW)
    private const val ERP_FM_STRONG_KW = 10.0
    private const val ERP_FM_WEAK_KW = 3.0
    private const val EIRP_AM_STRONG_KW = 10.0
    private const val ERP_TV_CHANNEL_KW = 25.0

    private fun calcIndex(envText: String, fmStrong: Int, fmWeak: Int, amStrong: Int, tvOpen: Int, tvAnt: Int, ctx: Context): Int {
        val envFactor = when {
            envText.equals(ctx.getString(R.string.ambient_rf_env_urban), true) -> 1.5
            envText.equals(ctx.getString(R.string.ambient_rf_env_rural), true) -> 0.5
            else -> 1.0
        }
        val base = (fmStrong * 2 + fmWeak * 1 + amStrong * 1 + tvOpen * 3 + tvAnt * 2)
        val score = (base * envFactor).toInt()
        return score.coerceIn(0, 100)
    }

    fun estimateFromPrefs(context: Context): Estimate {
        val prefs = context.getSharedPreferences("exposiguard_settings", Context.MODE_PRIVATE)
        val fmStrong = prefs.getInt("ambient_rf_fm_strong", 0)
        val fmWeak = prefs.getInt("ambient_rf_fm_weak", 0)
        val amStrong = prefs.getInt("ambient_rf_am_strong", 0)
        val tvOpen = prefs.getInt("ambient_rf_tv_open", 0)
        val tvAntenna = if (prefs.getBoolean("ambient_rf_tv_antenna", false)) prefs.getInt("ambient_rf_tv_antenna_channels", 0) else 0
        val envText = prefs.getString("ambient_rf_environment", context.getString(R.string.ambient_rf_env_suburban))
            ?: context.getString(R.string.ambient_rf_env_suburban)
        val profile = prefs.getString("ambient_rf_estimation_profile", context.getString(R.string.ambient_rf_profile_average))
            ?: context.getString(R.string.ambient_rf_profile_average)

        val index = calcIndex(envText, fmStrong, fmWeak, amStrong, tvOpen, tvAntenna, context)

        // Distancias nominales (m)
        val (rFmTv, rAm) = when {
            envText.equals(context.getString(R.string.ambient_rf_env_urban), true) -> 3000.0 to 5000.0
            envText.equals(context.getString(R.string.ambient_rf_env_rural), true) -> 10000.0 to 15000.0
            else -> 5000.0 to 8000.0
        }

        val nTvTotal = tvOpen + tvAntenna

        // ERP→EIRP
        val eirpFmStrongW = ERP_FM_STRONG_KW * 1000.0 * 1.64
        val eirpFmWeakW = ERP_FM_WEAK_KW * 1000.0 * 1.64
        val eirpTvW = ERP_TV_CHANNEL_KW * 1000.0 * 1.64
        val eirpAmW = EIRP_AM_STRONG_KW * 1000.0

        fun sAt(eirpW: Double, r: Double) = eirpW / (4.0 * Math.PI * r * r)

        val sFm = fmStrong * sAt(eirpFmStrongW, rFmTv) + fmWeak * sAt(eirpFmWeakW, rFmTv)
        val sTv = nTvTotal * sAt(eirpTvW, rFmTv)
        val sAm = amStrong * sAt(eirpAmW, rAm)
    val sTotal = sFm + sTv + sAm

        val alpha = when (profile) {
            context.getString(R.string.ambient_rf_profile_conservative) -> 0.05
            context.getString(R.string.ambient_rf_profile_max) -> 0.20
            else -> 0.10
        }
        val hardCap = 0.10 // W/kg
        val sarLike = (alpha * sTotal).coerceAtMost(hardCap)
        val sarBroadcast = (alpha * (sFm + sTv + sAm)).coerceAtMost(hardCap)

        return Estimate(
            index = index,
            sTotalWPerM2 = sTotal,
            sarLikeWPerKg = sarLike,
            sFmWPerM2 = sFm,
            sTvWPerM2 = sTv,
            sAmWPerM2 = sAm,
            sarBroadcastWPerKg = sarBroadcast
        )
    }
}
