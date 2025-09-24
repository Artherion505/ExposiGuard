package com.exposiguard.app.managers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import androidx.core.content.ContextCompat
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.pow

/**
 * Estima la exposición ambiente (cuerpo completo) debida a antenas de telefonía
 * a partir de la información de celdas cercanas (RSRP/RSCP/ARFCN, etc.).
 *
 * Notas importantes:
 * - Cálculo EXPERIMENTAL y muy aproximado. No se mezcla con % legal de SAR.
 * - Convierte potencia recibida (dBm) a densidad de potencia S (W/m²) asumiendo
 *   área efectiva de antena del terminal: Ae ≈ λ²·G/(4π) con G≈1.5 (1.76 dBi).
 * - Límites ICNIRP (público general):
 *   10–400 MHz: S_lim = 2 W/m²
 *   400–2000 MHz: S_lim = f/200 W/m² (f en MHz)
 *   >2000 MHz: S_lim = 10 W/m²
 */
class AmbientExposureManager @Inject constructor(private val context: Context) {

    data class CellAmbientEstimate(
        val tech: String,
        val frequencyHz: Double,
        val levelDbm: Double?,
        val densityWPerM2: Double,
        val percentOfLimit: Double
    )

    data class AmbientSummary(
        val totalDensityWPerM2: Double,
        val totalPercentOfLimit: Double,
        val cells: List<CellAmbientEstimate>
    )

    private fun hasRequiredPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        return fine && phone
    }

    @SuppressLint("MissingPermission")
    fun getAmbientSummary(): AmbientSummary? {
        if (!hasRequiredPermission()) return null

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cells = try { tm.allCellInfo } catch (_: SecurityException) { null } ?: return null

        val estimates = cells.mapNotNull { info ->
            when (info) {
                is CellInfoLte -> estimateForLte(info)
                is CellInfoNr -> estimateForNr(info)
                is CellInfoWcdma -> estimateForWcdma(info)
                is CellInfoGsm -> estimateForGsm(info)
                is CellInfoCdma -> estimateForCdma(info)
                else -> null
            }
        }

        if (estimates.isEmpty()) return null

        val totalS = estimates.sumOf { it.densityWPerM2 }.coerceAtLeast(0.0)
        // Para % agregado, usar el límite correspondiente a la celda dominante por S
        val dominant = estimates.maxByOrNull { it.densityWPerM2 }!!
        val totalPct = ((totalS / icnirpPowerDensityLimit(dominant.frequencyHz)) * 100.0).coerceAtLeast(0.0)

        return AmbientSummary(totalDensityWPerM2 = totalS, totalPercentOfLimit = totalPct, cells = estimates.sortedByDescending { it.densityWPerM2 })
    }

    private fun estimateForLte(info: CellInfoLte): CellAmbientEstimate? {
        val ss = info.cellSignalStrength
        val ci = info.cellIdentity
        val rsrp = ss.rsrp.takeIf { it != CellInfo.UNAVAILABLE }?.toDouble()
        val earfcn = if (Build.VERSION.SDK_INT >= 29) ci.earfcn else null
        val freq = lteEarfcnToHz(earfcn) ?: defaultFreqHzForTech("LTE")
        val s = rsrpDbmToDensityWPerM2(rsrp, freq)
        val pct = (s / icnirpPowerDensityLimit(freq) * 100.0).coerceAtLeast(0.0)
        return CellAmbientEstimate("LTE", freq, rsrp, s, pct)
    }

    private fun estimateForNr(info: CellInfoNr): CellAmbientEstimate? {
        val rsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val nrSs = info.cellSignalStrength as? android.telephony.CellSignalStrengthNr
            val v = nrSs?.ssRsrp ?: info.cellSignalStrength.dbm
            if (v != android.telephony.CellInfo.UNAVAILABLE) v.toDouble() else info.cellSignalStrength.dbm.toDouble()
        } else info.cellSignalStrength.dbm.toDouble()
        val nrarfcn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (info.cellIdentity as? android.telephony.CellIdentityNr)?.nrarfcn
        } else null
        val freq = nrArfcnToHz(nrarfcn) ?: defaultFreqHzForTech("NR")
        val s = rsrpDbmToDensityWPerM2(rsrp, freq)
        val pct = (s / icnirpPowerDensityLimit(freq) * 100.0).coerceAtLeast(0.0)
        return CellAmbientEstimate("5G NR", freq, rsrp, s, pct)
    }

    private fun estimateForWcdma(info: CellInfoWcdma): CellAmbientEstimate? {
        val ss = info.cellSignalStrength
        val ci = info.cellIdentity
        val rscp = ss.dbm.takeIf { it != CellInfo.UNAVAILABLE }?.toDouble()
        // UARFCN a Hz si está disponible
        val uarfcn = if (Build.VERSION.SDK_INT >= 30) ci.uarfcn else null
        val freq = uarfcnToHz(uarfcn) ?: 2_100e6 // 2100 MHz típico
        val s = rsrpDbmToDensityWPerM2(rscp, freq)
        val pct = (s / icnirpPowerDensityLimit(freq) * 100.0).coerceAtLeast(0.0)
        return CellAmbientEstimate("WCDMA", freq, rscp, s, pct)
    }

    private fun estimateForGsm(info: CellInfoGsm): CellAmbientEstimate? {
        val ss = info.cellSignalStrength
        val ci = info.cellIdentity
        val rssi = ss.dbm.takeIf { it != CellInfo.UNAVAILABLE }?.toDouble()
        val arfcn = if (Build.VERSION.SDK_INT >= 24) ci.arfcn else null
        val freq = gsmArfcnToHz(arfcn) ?: 900e6
        val s = rsrpDbmToDensityWPerM2(rssi, freq)
        val pct = (s / icnirpPowerDensityLimit(freq) * 100.0).coerceAtLeast(0.0)
        return CellAmbientEstimate("GSM", freq, rssi, s, pct)
    }

    private fun estimateForCdma(info: CellInfoCdma): CellAmbientEstimate? {
        val ss = info.cellSignalStrength
        val dbm = ss.dbm.takeIf { it != CellInfo.UNAVAILABLE }?.toDouble()
        val freq = 850e6 // aproximación banda celular 850 MHz
        val s = rsrpDbmToDensityWPerM2(dbm, freq)
        val pct = (s / icnirpPowerDensityLimit(freq) * 100.0).coerceAtLeast(0.0)
        return CellAmbientEstimate("CDMA", freq, dbm, s, pct)
    }

    // Conversión principal: dBm -> W y W -> densidad suponiendo Ae
    private fun rsrpDbmToDensityWPerM2(levelDbm: Double?, freqHz: Double): Double {
        if (levelDbm == null) return 0.0
        // Potencia recibida en W (dBm a W)
        val prW = 10.0.pow((levelDbm - 30.0) / 10.0).coerceAtLeast(0.0)
        val lambda = 299_792_458.0 / freqHz
        val gain = 1.5 // ganancia efectiva ~1.76 dBi
        val ae = (lambda * lambda * gain) / (4.0 * PI)
        if (ae <= 0.0) return 0.0
        val s = (prW / ae)
        return s.coerceAtLeast(0.0)
    }

    // Límite ICNIRP (aprox 1998/2020) en función de la frecuencia
    private fun icnirpPowerDensityLimit(freqHz: Double): Double {
        val fMHz = freqHz / 1e6
        return when {
            fMHz < 400.0 -> 2.0
            fMHz <= 2000.0 -> fMHz / 200.0 // 400–2000 MHz: f/200 W/m²
            else -> 10.0 // > 2 GHz
        }
    }

    // Mapas de frecuencia (aproximados) a partir de ARFCN/EARFCN/NRARFCN
    private fun lteEarfcnToHz(earfcn: Int?): Double? {
        if (earfcn == null || earfcn <= 0) return null
        // Simplificación: bandas comunes DL
        return when (earfcn) {
            in 1200..1949 -> 1_800e6 // Band 3
            in 500..599 -> 850e6    // Band 5
            in 2750..3449 -> 2_600e6 // Band 7
            in 3450..3799 -> 700e6  // Band 28
            in 36200..36349 -> 2_300e6 // Band 40
            else -> null
        }
    }

    private fun nrArfcnToHz(nrarfcn: Int?): Double? {
        if (nrarfcn == null || nrarfcn <= 0) return null
        // Rango FR1 aproximado: 410–7125 MHz; fórmula exacta depende de tablas.
        // Usar buckets simples
        return when (nrarfcn) {
            in 0..599999 -> 3_500e6 // FR1 típico (n78)
            in 600000..2016666 -> 28_000e6 // FR2 mmWave aproximado
            else -> null
        }
    }

    private fun uarfcnToHz(uarfcn: Int?): Double? {
        if (uarfcn == null || uarfcn <= 0) return null
        // WCDMA bandas comunes
        return 2_100e6
    }

    private fun gsmArfcnToHz(arfcn: Int?): Double? {
        if (arfcn == null || arfcn <= 0) return null
        return when (arfcn) {
            in 0..124 -> 900e6 // GSM 900
            in 512..885 -> 1_800e6 // DCS 1800
            else -> null
        }
    }

    private fun defaultFreqHzForTech(tech: String): Double = when (tech) {
        "LTE" -> 1_800e6
        "NR" -> 3_500e6
        else -> 1_800e6
    }
}
