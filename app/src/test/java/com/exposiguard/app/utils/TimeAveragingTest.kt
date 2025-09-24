package com.exposiguard.app.utils

import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAveragingTest {

    private fun r(t: Long, v: Double) = ExposureReading(
        timestamp = t,
        wifiLevel = v,
        sarLevel = 0.0,
        bluetoothLevel = 0.0,
        type = ExposureType.WIFI,
        source = "test"
    )

    @Test
    fun `twa simple zero order hold`() {
        val t0 = 0L
        val r = listOf(
            ExposureReading(t0 + 0, 1.0, 0.0, 0.0, ExposureType.WIFI, "t0"),
            ExposureReading(t0 + 10_000, 3.0, 0.0, 0.0, ExposureType.WIFI, "t10"),
        )
        val twa = TimeAveraging.timeWeightedAverage(r, t0, t0 + 20_000, { it.wifiLevel }, null)
        // 0-10s value=1 => area 10; 10-20s value=3 => area 30; avg = (10+30)/20 = 2.0
        assertEquals(2.0, twa, 1e-9)
    }

    @Test
    fun `twa with max gap caps`() {
        val t0 = 0L
        val r = listOf(
            ExposureReading(t0 + 0, 5.0, 0.0, 0.0, ExposureType.WIFI, "t0"),
        )
        val twa = TimeAveraging.timeWeightedAverage(r, t0, t0 + 60_000, { it.wifiLevel }, 10_000)
        // After 10s cap, area=5*10; total time=60s => 50/60 ≈ 0.8333
        assertEquals(0.8333, twa, 1e-3)
    }

    @Test
    fun `constant value over window yields same average`() {
        val start = 0L
        val end = 10_000L
        val readings = listOf(
            r(0, -50.0),
            r(5_000, -50.0)
        )
        val avg = TimeAveraging.timeWeightedAverage(readings, start, end, { it.wifiLevel }, null)
        assertEquals(-50.0, avg, 1e-6)
    }

    @Test
    fun `step change halves average`() {
        val start = 0L
        val end = 10_000L
        val readings = listOf(
            r(0, 0.0),
            r(5_000, 10.0)
        )
        val avg = TimeAveraging.timeWeightedAverage(readings, start, end, { it.wifiLevel }, null)
        assertEquals(5.0, avg, 1e-6)
    }

    @Test
    fun `max gap caps area contribution`() {
        val start = 0L
        val end = 30_000L
        val readings = listOf(
            r(0, 10.0), // hold 10
            r(20_000, 0.0) // at t=20s drops to 0
        )
        // Without cap: area = 10*20s + 0*10s = 200, avg=200/30=6.666...
        val avgNoCap = TimeAveraging.timeWeightedAverage(readings, start, end, { it.wifiLevel }, null)
        assertEquals(200.0/30.0, avgNoCap, 1e-6)

        // With cap 5s: first segment limited to 5s, last segment limited to 5s
        val avgCap = TimeAveraging.timeWeightedAverage(readings, start, end, { it.wifiLevel }, 5_000)
        // area = 10*5s + 0*5s = 50, avg = 50/30 = 1.666...
        assertEquals(50.0/30.0, avgCap, 1e-6)
    }

    @Test
    fun `initial value before window is respected`() {
        val readings = listOf(
            r(-5_000, 3.0),
            r(5_000, 1.0)
        )
        val avg = TimeAveraging.timeWeightedAverage(readings, 0, 10_000, { it.wifiLevel }, null)
        // 0..5s -> 3.0, 5..10s -> 1.0 => (3*5 + 1*5)/10 = 2.0
        assertEquals(2.0, avg, 1e-6)
    }
}
