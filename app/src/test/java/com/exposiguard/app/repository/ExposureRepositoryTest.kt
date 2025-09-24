package com.exposiguard.app.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExposureRepositoryTest {
    @Test
    fun `components twa over window`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ExposureRepository(context)
        repo.clearAllData()
        val t0 = 1_000_000L
        val readings = listOf(
            ExposureReading(t0 + 0, 1.0, 0.5, 0.2, ExposureType.WIFI, "a"),
            ExposureReading(t0 + 10_000, 3.0, 1.5, 0.6, ExposureType.SAR, "b"),
        )
        repo.addExposureReadings(readings)

        val (wifi, sar, bt) = repo.timeWeightedAverageComponents(t0, t0 + 20_000)
        // 0-10s: (1,0.5,0.2), 10-20s: (3,1.5,0.6)
        // WiFi avg = (1*10 + 3*10)/20 = 2.0
        // SAR avg = (0.5*10 + 1.5*10)/20 = 1.0
        // BT  avg = (0.2*10 + 0.6*10)/20 = 0.4
        assertEquals(2.0, wifi, 1e-9)
        assertEquals(1.0, sar, 1e-9)
        assertEquals(0.4, bt, 1e-9)
    }
}
