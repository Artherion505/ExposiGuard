package com.exposiguard.app.repository

import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.exposiguard.app.utils.TimeAveraging
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de "integración" ligeros para validar la lógica de TWA del repositorio
 * usando la misma agregación de componentes, sin depender de Android Context.
 */
class ExposureRepositoryTwaTest {

    private fun r(t: Long, wifi: Double = 0.0, sar: Double = 0.0, bt: Double = 0.0) = ExposureReading(
        timestamp = t,
        wifiLevel = wifi,
        sarLevel = sar,
        bluetoothLevel = bt,
        type = ExposureType.WIFI,
        source = "test"
    )

    private fun twaTotal(readings: List<ExposureReading>, start: Long, end: Long, maxGapMs: Long? = null): Double {
        val wifi = TimeAveraging.timeWeightedAverage(readings, start, end, { it.wifiLevel }, maxGapMs)
        val sar = TimeAveraging.timeWeightedAverage(readings, start, end, { it.sarLevel }, maxGapMs)
        val bt = TimeAveraging.timeWeightedAverage(readings, start, end, { it.bluetoothLevel }, maxGapMs)
        return wifi + sar + bt
    }

    @Test
    fun `repo twa components match expected`() {
        val start = 0L
        val end = 10_000L
        val readings = listOf(
            r(0, wifi = 2.0, sar = 0.5, bt = 0.0),
            r(5_000, wifi = 4.0, sar = 1.5, bt = 0.2)
        )

        val wifi = TimeAveraging.timeWeightedAverage(readings, start, end, { it.wifiLevel }, null)
        val sar = TimeAveraging.timeWeightedAverage(readings, start, end, { it.sarLevel }, null)
        val bt = TimeAveraging.timeWeightedAverage(readings, start, end, { it.bluetoothLevel }, null)

        // Promedio por mitades: (2+4)/2=3, (0.5+1.5)/2=1.0, (0.0+0.2)/2=0.1
        assertEquals(3.0, wifi, 1e-6)
        assertEquals(1.0, sar, 1e-6)
        assertEquals(0.1, bt, 1e-6)

        val total = twaTotal(readings, start, end, null)
        assertEquals(4.1, total, 1e-6)
    }

    @Test
    fun `repo twa respects maxGap cap`() {
        val start = 0L
        val end = 30_000L
        val readings = listOf(
            r(0, wifi = 10.0),
            r(20_000, wifi = 0.0)
        )

        val totalNoCap = twaTotal(readings, start, end, null)
        assertEquals(200.0/30.0, totalNoCap, 1e-6)

        val totalCap5 = twaTotal(readings, start, end, 5_000)
        assertEquals(50.0/30.0, totalCap5, 1e-6)
    }
}
