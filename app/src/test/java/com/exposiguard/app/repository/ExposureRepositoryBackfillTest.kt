package com.exposiguard.app.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class ExposureRepositoryBackfillTest {

    @Test
    fun `second reading triggers 5min backfill`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ExposureRepository(context)
        repo.clearAllData()

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now

        // Dos lecturas dentro del mismo día, cercanas al presente para asegurar que caen en el rango
        val t1 = now - 30 * 60 * 1000 // hace 30 min
        val t2 = now - 5 * 60 * 1000  // hace 5 min

        repo.addExposureReading(
            ExposureReading(
                timestamp = t1,
                wifiLevel = 1.0,
                sarLevel = 0.5,
                bluetoothLevel = 0.2,
                type = ExposureType.WIFI,
                source = "test"
            )
        )

        repo.addExposureReading(
            ExposureReading(
                timestamp = t2,
                wifiLevel = 3.0,
                sarLevel = 1.5,
                bluetoothLevel = 0.6,
                type = ExposureType.SAR,
                source = "test"
            )
        )

        val all = repo.getAllReadings()
        val backfilled = all.filter { it.source == "Backfill" }

        // Debe existir al menos una lectura de backfill alineada a 5 minutos
        assertTrue("No se generaron lecturas de backfill", backfilled.isNotEmpty())
        assertTrue("Las lecturas de backfill deben estar alineadas a buckets de 5 minutos",
            backfilled.all { it.timestamp % (5 * 60 * 1000L) == 0L }
        )
    }
}
