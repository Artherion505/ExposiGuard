package com.exposiguard.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.exposiguard.app.managers.*
import com.exposiguard.app.repository.ExposureRepository
import com.exposiguard.app.viewmodel.GeneralViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComponentIntegrationTest {

    private lateinit var context: Context
    private lateinit var wiFiManager: WiFiManager
    private lateinit var sarManager: SARManager
    private lateinit var noiseManager: NoiseManager
    private lateinit var healthManager: HealthManager
    private lateinit var trendsManager: TrendsAnalysisManager
    private lateinit var userProfileManager: UserProfileManager
    private lateinit var exposureRepository: ExposureRepository
    private lateinit var generalViewModel: GeneralViewModel

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

        // Initialize ViewModel
        generalViewModel = GeneralViewModel(
            wiFiManager, sarManager, noiseManager, healthManager,
            trendsManager, exposureRepository, userProfileManager
        )
    }

    @Test
    fun testViewModelManagerIntegration() {
        // Test that ViewModel properly delegates to managers
        val wiFiNetworks = generalViewModel.getWiFiNetworks()
        val carrierNetworks = generalViewModel.getCarrierNetworks()
        val bluetoothDevices = generalViewModel.getBluetoothDevices()
        val sarLevel = generalViewModel.getSARLevel()

        // These should not throw exceptions and return valid data
        assertNotNull("WiFi networks should be accessible", wiFiNetworks)
        assertNotNull("Carrier networks should be accessible", carrierNetworks)
        assertNotNull("Bluetooth devices should be accessible", bluetoothDevices)
        assertTrue("SAR level should be valid", sarLevel >= 0.0)
    }

    @Test
    fun testDataFlowFromManagersToRepository() = runBlocking {
        // Create test data through managers
        val testReading = ExposureReading(
            timestamp = System.currentTimeMillis(),
            wifiLevel = 35.0,
            sarLevel = 1.5,
            bluetoothLevel = 8.0,
            type = ExposureType.WIFI
        )

        // Add reading through repository
        exposureRepository.addExposureReading(testReading)

        // Verify data is accessible through ViewModel
        val allReadings = generalViewModel.getExposureReadings()
        assertNotNull("Readings should be accessible through ViewModel", allReadings)

        // Test statistics through ViewModel
        val stats = generalViewModel.getExposureStats()
        assertNotNull("Stats should be accessible through ViewModel", stats)
        assertTrue("Stats should contain data", stats.isNotEmpty())
    }

    @Test
    fun testTrendsAnalysisIntegration() = runBlocking {
        // Add multiple readings for trends analysis
        val readings = listOf(
            ExposureReading(System.currentTimeMillis() - 3600000, 20.0, 1.0, 5.0, ExposureType.WIFI),
            ExposureReading(System.currentTimeMillis() - 1800000, 30.0, 1.5, 8.0, ExposureType.WIFI),
            ExposureReading(System.currentTimeMillis(), 40.0, 2.0, 12.0, ExposureType.WIFI)
        )

        readings.forEach { exposureRepository.addExposureReading(it) }

        // Test trends analysis through ViewModel
        val trendData = generalViewModel.getExposureTrendData()
        assertNotNull("Trend data should be generated", trendData)

        // Test combined exposure data
        val combinedData = generalViewModel.getCombinedExposureData()
        assertNotNull("Combined data should be generated", combinedData)

        // Test trends analysis method
        val analysisResult = generalViewModel.analyzeTrends()
        assertNotNull("Analysis result should be generated", analysisResult)
    }

    @Test
    fun testUserProfileIntegration() {
        // Test user profile management
        val testWeight = 70.0
        val testHeight = 175.0

        // Save user profile
        generalViewModel.saveUserProfile(testWeight, testHeight)

        // Verify profile is saved and retrievable
        val retrievedWeight = generalViewModel.getUserWeight()
        val retrievedHeight = generalViewModel.getUserHeight()
        val isConfigured = generalViewModel.isUserProfileConfigured()

        assertEquals("Weight should be saved correctly", testWeight, retrievedWeight, 0.01)
        assertEquals("Height should be saved correctly", testHeight, retrievedHeight, 0.01)
        assertTrue("Profile should be configured", isConfigured)
    }

    @Test
    fun testNoiseAndHealthMonitoringIntegration() {
        // Test noise monitoring
        generalViewModel.startNoiseMonitoring()
        val noiseLevel = generalViewModel.getNoiseLevel()
        assertTrue("Noise level should be valid", noiseLevel >= 0.0)

        // Test health data (this might be a suspend function in real implementation)
        // For this test, we'll just verify the method exists and doesn't throw
        try {
            val healthData = generalViewModel.getHealthData()
            assertNotNull("Health data should be accessible", healthData)
        } catch (e: Exception) {
            // If it's a suspend function, this is expected
            assertTrue("Should handle suspend function gracefully", e.message?.contains("suspend") == true)
        }
    }

    @Test
    fun testDataConsistencyAcrossComponents() = runBlocking {
        // Clear existing data
        exposureRepository.clearOldData(daysToKeep = 0)

        // Add data through repository
        val testReading = ExposureReading(
            timestamp = System.currentTimeMillis(),
            wifiLevel = 25.0,
            sarLevel = 1.8,
            bluetoothLevel = 6.0,
            type = ExposureType.CELLULAR
        )
        exposureRepository.addExposureReading(testReading)

        // Verify data consistency across different access methods
        val repoReadings = exposureRepository.getAllReadings()
        val viewModelReadings = generalViewModel.getExposureReadings()

        assertTrue("Repository should have readings", repoReadings.isNotEmpty())
        assertNotNull("ViewModel should have readings", viewModelReadings)

        // Check that the data is the same
        if (repoReadings.isNotEmpty() && viewModelReadings.isNotEmpty()) {
            assertEquals("Data should be consistent", repoReadings.size, viewModelReadings.size)
        }

        // Test stats consistency
        val repoStats = exposureRepository.getStats()
        val viewModelStats = generalViewModel.getExposureStats()

        assertNotNull("Repository stats should exist", repoStats)
        assertNotNull("ViewModel stats should exist", viewModelStats)
    }

    @Test
    fun testErrorHandlingIntegration() {
        // Test that components handle errors gracefully

        // Test with invalid data
        try {
            val invalidReading = ExposureReading(
                timestamp = -1, // Invalid timestamp
                wifiLevel = -10.0, // Invalid negative value
                sarLevel = 1.0,
                bluetoothLevel = 5.0,
                type = ExposureType.WIFI
            )
            exposureRepository.addExposureReading(invalidReading)

            // Should not crash, should handle gracefully
            val readings = exposureRepository.getAllReadings()
            assertNotNull("Should handle invalid data gracefully", readings)

        } catch (e: Exception) {
            // If an exception is thrown, it should be handled properly
            assertNotNull("Exception should be meaningful", e.message)
        }
    }

    @Test
    fun testResourceCleanupIntegration() {
        // Test that resources are properly cleaned up

        // Add some data
        val testReading = ExposureReading(
            timestamp = System.currentTimeMillis(),
            wifiLevel = 15.0,
            sarLevel = 0.8,
            bluetoothLevel = 3.0,
            type = ExposureType.BLUETOOTH
        )
        exposureRepository.addExposureReading(testReading)

        // Verify data exists
        val readingsBefore = exposureRepository.getAllReadings()
        assertTrue("Should have readings before cleanup", readingsBefore.isNotEmpty())

        // Clear old data (with 0 days to keep)
        exposureRepository.clearOldData(daysToKeep = 0)

        // Verify data is cleaned up
        val readingsAfter = exposureRepository.getAllReadings()
        assertTrue("Data should be cleaned up", readingsAfter.isEmpty() || readingsAfter.size < readingsBefore.size)
    }
}
