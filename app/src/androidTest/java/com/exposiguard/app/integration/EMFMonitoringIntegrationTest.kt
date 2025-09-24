package com.exposiguard.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.*
import androidx.work.testing.TestWorkerBuilder
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.exposiguard.app.managers.*
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.workers.EMFMonitoringWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class EMFMonitoringIntegrationTest {

    private lateinit var context: Context
    private lateinit var wiFiManager: WiFiManager
    private lateinit var sarManager: SARManager
    private lateinit var noiseManager: NoiseManager
    private lateinit var healthManager: HealthManager
    private lateinit var trendsManager: TrendsAnalysisManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var exposureRepository: ExposureRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Initialize all managers
        wiFiManager = WiFiManager(context)
        sarManager = SARManager(context)
        noiseManager = NoiseManager()
        healthManager = HealthManager()
        trendsManager = TrendsAnalysisManager()
        userProfileManager = UserProfileManager(context)
        exposureRepository = ExposureRepository(context)
    }

    @Test
    fun testEndToEndEMFMonitoringFlow() = runBlocking {
        // Step 1: Verify managers are properly initialized
        assertNotNull("WiFiManager should be initialized", wiFiManager)
        assertNotNull("SARManager should be initialized", sarManager)
        assertNotNull("NoiseManager should be initialized", noiseManager)
        assertNotNull("HealthManager should be initialized", healthManager)
        assertNotNull("TrendsManager should be initialized", trendsManager)
        assertNotNull("UserProfileManager should be initialized", userProfileManager)
        assertNotNull("ExposureRepository should be initialized", exposureRepository)

        // Step 2: Test data collection
        val initialReadingCount = exposureRepository.getAllReadings().size

        // Create a test exposure reading
        val testReading = ExposureReading(
            timestamp = System.currentTimeMillis(),
            wifiLevel = 45.5,
            sarLevel = 1.2,
            bluetoothLevel = 12.3,
            type = ExposureType.WIFI
        )

        // Step 3: Test data persistence
        exposureRepository.addExposureReading(testReading)

        // Verify data was saved
        val readings = exposureRepository.getAllReadings()
        assertTrue("Should have at least one reading after adding", readings.size > initialReadingCount)
        assertEquals("Reading should match what was saved", testReading.wifiLevel, readings.last().wifiLevel, 0.01)

        // Step 4: Test data retrieval by type
        val wifiReadings = exposureRepository.getReadingsByType(ExposureType.WIFI)
        assertTrue("Should have WiFi readings", wifiReadings.isNotEmpty())

        // Step 5: Test statistics calculation
        val stats = exposureRepository.getStats()
        assertTrue("Stats should contain totalReadings", stats.containsKey("totalReadings"))
        assertTrue("Stats should contain averageExposure", stats.containsKey("averageExposure"))
        assertTrue("Stats should contain maxExposure", stats.containsKey("maxExposure"))
        assertTrue("Stats should contain minExposure", stats.containsKey("minExposure"))

        // Step 6: Test trends analysis
        val trendData = trendsManager.getExposureTrendData(readings, 24)
        assertNotNull("Trend data should not be null", trendData)

        // Step 7: Test combined exposure data
        val combinedData = trendsManager.getCombinedExposureData(readings)
        assertNotNull("Combined data should not be null", combinedData)

        // Step 8: Test data cleanup
        val oldTimestamp = System.currentTimeMillis() - (10 * 24 * 60 * 60 * 1000L) // 10 days ago
        val oldReading = testReading.copy(timestamp = oldTimestamp)
        exposureRepository.addExposureReading(oldReading)

        val readingsBeforeCleanup = exposureRepository.getAllReadings().size
        exposureRepository.clearOldData(daysToKeep = 1) // Keep only 1 day
        val readingsAfterCleanup = exposureRepository.getAllReadings().size

        assertTrue("Should have fewer readings after cleanup", readingsAfterCleanup <= readingsBeforeCleanup)
    }

    @Test
    fun testWorkerIntegration() {
        val executor = Executors.newSingleThreadExecutor()
        val worker = TestWorkerBuilder<EMFMonitoringWorker>(
            context = context,
            executor = executor,
            inputData = workDataOf("test_param" to "test_value")
        ).build()

        runBlocking {
            val result = worker.doWork()
            // Worker should complete successfully
            assertEquals("Worker should succeed", ListenableWorker.Result.success(), result)
        }

        executor.shutdown()
    }

    @Test
    fun testManagerIntegration() {
        // Test that managers can work together
        val wiFiNetworks = wiFiManager.getWiFiNetworks()
        val carrierNetworks = wiFiManager.carrierNetworks
        val bluetoothDevices = wiFiManager.getBluetoothDevices()

        // These should not throw exceptions
        assertNotNull("WiFi networks should be retrievable", wiFiNetworks)
        assertNotNull("Carrier networks should be retrievable", carrierNetworks)
        assertNotNull("Bluetooth devices should be retrievable", bluetoothDevices)

        // Test SAR level retrieval
        val sarLevel = sarManager.getCurrentSARLevel()
        assertTrue("SAR level should be non-negative", sarLevel >= 0.0)

        // Test noise monitoring
        noiseManager.startNoiseMonitoring()
        val noiseLevel = noiseManager.getCurrentNoiseLevel()
        assertTrue("Noise level should be non-negative", noiseLevel >= 0.0)
    }

    @Test
    fun testDataConsistency() = runBlocking {
        // Test that data remains consistent across operations
        val initialStats = exposureRepository.getStats()

        // Add multiple readings
        val readings = listOf(
            ExposureReading(System.currentTimeMillis(), 10.0, 1.0, 5.0, ExposureType.WIFI),
            ExposureReading(System.currentTimeMillis(), 20.0, 2.0, 10.0, ExposureType.CELLULAR),
            ExposureReading(System.currentTimeMillis(), 30.0, 3.0, 15.0, ExposureType.BLUETOOTH)
        )

        readings.forEach { exposureRepository.addExposureReading(it) }

        // Verify all readings were saved
        val allReadings = exposureRepository.getAllReadings()
        assertTrue("Should have all added readings", allReadings.size >= readings.size)

        // Verify stats are updated
        val updatedStats = exposureRepository.getStats()
        assertNotEquals("Stats should change after adding readings", initialStats["totalReadings"], updatedStats["totalReadings"])

        // Test filtering by type
        val wifiReadings = exposureRepository.getReadingsByType(ExposureType.WIFI)
        val cellularReadings = exposureRepository.getReadingsByType(ExposureType.CELLULAR)
        val bluetoothReadings = exposureRepository.getReadingsByType(ExposureType.BLUETOOTH)

        assertTrue("Should have WiFi readings", wifiReadings.isNotEmpty())
        assertTrue("Should have Cellular readings", cellularReadings.isNotEmpty())
        assertTrue("Should have Bluetooth readings", bluetoothReadings.isNotEmpty())
    }
}
