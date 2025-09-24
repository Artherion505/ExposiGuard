package com.exposiguard.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.exposiguard.app.repository.ExposureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class StoragePerformanceIntegrationTest {

    private lateinit var context: Context
    private lateinit var exposureRepository: ExposureRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        exposureRepository = ExposureRepository(context)

        // Clear any existing data
        exposureRepository.clearOldData(daysToKeep = 0)
    }

    @Test
    fun testBulkDataInsertionPerformance() = runBlocking {
        val batchSizes = listOf(10, 50, 100, 500)

        for (batchSize in batchSizes) {
            // Clear data before each test
            exposureRepository.clearOldData(daysToKeep = 0)

            val readings = generateTestReadings(batchSize)

            val insertionTime = measureTimeMillis {
                readings.forEach { exposureRepository.addExposureReading(it) }
            }

            val avgTimePerReading = insertionTime.toDouble() / batchSize

            println("Batch size: $batchSize, Total time: ${insertionTime}ms, Avg per reading: ${avgTimePerReading}ms")

            // Verify all readings were saved
            val savedReadings = exposureRepository.getAllReadings()
            assertEquals("All readings should be saved", batchSize, savedReadings.size)

            // Performance assertions (adjust thresholds based on device capabilities)
            assertTrue("Insertion should be reasonably fast", avgTimePerReading < 50.0)
        }
    }

    @Test
    fun testDataRetrievalPerformance() = runBlocking {
        // Insert test data
        val testReadings = generateTestReadings(200)
        testReadings.forEach { exposureRepository.addExposureReading(it) }

        // Test retrieval performance
        val retrievalTime = measureTimeMillis {
            val readings = exposureRepository.getAllReadings()
            assertEquals("Should retrieve all readings", 200, readings.size)
        }

        println("Data retrieval time: ${retrievalTime}ms")

        // Test filtering performance
        val filterTime = measureTimeMillis {
            val wifiReadings = exposureRepository.getReadingsByType(ExposureType.WIFI)
            val cellularReadings = exposureRepository.getReadingsByType(ExposureType.CELLULAR)
            val bluetoothReadings = exposureRepository.getReadingsByType(ExposureType.BLUETOOTH)

            assertTrue("Should have readings of each type", wifiReadings.isNotEmpty())
            assertTrue("Should have readings of each type", cellularReadings.isNotEmpty())
            assertTrue("Should have readings of each type", bluetoothReadings.isNotEmpty())
        }

        println("Data filtering time: ${filterTime}ms")

        // Performance assertions
        assertTrue("Retrieval should be fast", retrievalTime < 100)
        assertTrue("Filtering should be fast", filterTime < 50)
    }

    @Test
    fun testStatisticsCalculationPerformance() = runBlocking {
        // Insert large dataset
        val largeDataset = generateTestReadings(1000)
        largeDataset.forEach { exposureRepository.addExposureReading(it) }

        val statsTime = measureTimeMillis {
            val stats = exposureRepository.getStats()
            assertNotNull("Stats should be calculated", stats)
            assertTrue("Should have total readings", stats.containsKey("totalReadings"))
            assertEquals("Should have correct total", 1000, stats["totalReadings"])
        }

        println("Statistics calculation time: ${statsTime}ms")

        // Performance assertion
        assertTrue("Statistics calculation should be reasonably fast", statsTime < 200)
    }

    @Test
    fun testMemoryUsageOptimization() = runBlocking {
        // Test with different data sizes to check memory efficiency
        val dataSizes = listOf(100, 500, 1000)

        for (size in dataSizes) {
            exposureRepository.clearOldData(daysToKeep = 0)

            val readings = generateTestReadings(size)
            readings.forEach { exposureRepository.addExposureReading(it) }

            // Force garbage collection to get more accurate memory measurements
            System.gc()
            Thread.sleep(100)

            val memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

            // Perform operations that should not cause excessive memory usage
            val allReadings = exposureRepository.getAllReadings()
            val stats = exposureRepository.getStats()
            val filteredReadings = exposureRepository.getReadingsByType(ExposureType.WIFI)

            System.gc()
            Thread.sleep(100)

            val memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memoryDelta = memoryAfter - memoryBefore

            println("Data size: $size, Memory delta: ${memoryDelta / 1024}KB")

            // Verify operations completed successfully
            assertEquals("Should retrieve all readings", size, allReadings.size)
            assertNotNull("Stats should be calculated", stats)
            assertTrue("Should have filtered results", filteredReadings.isNotEmpty())

            // Memory usage should not grow excessively
            assertTrue("Memory usage should be reasonable", memoryDelta < 10 * 1024 * 1024) // Less than 10MB
        }
    }

    @Test
    fun testDataCleanupPerformance() = runBlocking {
        // Insert readings with different timestamps
        val now = System.currentTimeMillis()
        val oldReadings = generateTestReadings(100).map {
            it.copy(timestamp = now - (10 * 24 * 60 * 60 * 1000L)) // 10 days ago
        }
        val recentReadings = generateTestReadings(50).map {
            it.copy(timestamp = now - (1 * 60 * 60 * 1000L)) // 1 hour ago
        }

        (oldReadings + recentReadings).forEach { exposureRepository.addExposureReading(it) }

        val readingsBeforeCleanup = exposureRepository.getAllReadings().size

        val cleanupTime = measureTimeMillis {
            exposureRepository.clearOldData(daysToKeep = 1) // Keep only 1 day
        }

        val readingsAfterCleanup = exposureRepository.getAllReadings().size

        println("Cleanup time: ${cleanupTime}ms, Readings before: $readingsBeforeCleanup, after: $readingsAfterCleanup")

        // Verify cleanup worked
        assertTrue("Should have fewer readings after cleanup", readingsAfterCleanup < readingsBeforeCleanup)
        assertTrue("Should keep recent readings", readingsAfterCleanup >= 50)

        // Performance assertion
        assertTrue("Cleanup should be fast", cleanupTime < 100)
    }

    private fun generateTestReadings(count: Int): List<ExposureReading> {
        val types = listOf(ExposureType.WIFI, ExposureType.CELLULAR, ExposureType.BLUETOOTH)
        return (1..count).map { i ->
            ExposureReading(
                timestamp = System.currentTimeMillis() + i,
                wifiLevel = (10..50).random().toDouble(),
                sarLevel = (0.5..3.0).random(),
                bluetoothLevel = (5..25).random().toDouble(),
                type = types.random()
            )
        }
    }
}
