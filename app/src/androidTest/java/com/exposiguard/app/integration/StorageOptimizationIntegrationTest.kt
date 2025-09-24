package com.exposiguard.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.exposiguard.app.repository.ExposureRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class StorageOptimizationIntegrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exposureRepository = ExposureRepository(context)

    @Test
    fun testBatchInsertOptimization() = runBlocking {
        // Clear existing data
        exposureRepository.clearOldData(daysToKeep = 0)

        // Test batch insertion performance
        val batchSizes = listOf(10, 50, 100)

        for (batchSize in batchSizes) {
            val readings = generateTestReadings(batchSize)

            val insertTime = measureTimeMillis {
                // Simulate batch insert by adding all readings
                readings.forEach { exposureRepository.addExposureReading(it) }
            }

            val avgTimePerInsert = insertTime.toDouble() / batchSize
            println("Batch size $batchSize: ${insertTime}ms total, ${avgTimePerInsert}ms per insert")

            // Verify all data was saved
            val savedReadings = exposureRepository.getAllReadings()
            assertEquals("All readings should be saved", batchSize, savedReadings.size)

            // Performance check
            assertTrue("Batch insert should be efficient", avgTimePerInsert < 20.0)
        }
    }

    @Test
    fun testDataCompressionOptimization() = runBlocking {
        // Test data size before and after operations
        val initialReadings = generateTestReadings(100)
        initialReadings.forEach { exposureRepository.addExposureReading(it) }

        val readingsBefore = exposureRepository.getAllReadings()

        // Perform operations that might trigger data compression/serialization
        val stats = exposureRepository.getStats()
        val filteredReadings = exposureRepository.getReadingsByType(ExposureType.WIFI)

        val readingsAfter = exposureRepository.getAllReadings()

        // Data should remain consistent
        assertEquals("Data should remain consistent", readingsBefore.size, readingsAfter.size)
        assertNotNull("Stats should be generated", stats)
        assertTrue("Should have filtered results", filteredReadings.isNotEmpty())
    }

    @Test
    fun testMemoryEfficientDataStructures() = runBlocking {
        // Test with large dataset to verify memory efficiency
        val largeDataset = generateTestReadings(500)
        largeDataset.forEach { exposureRepository.addExposureReading(it) }

        // Test memory usage during operations
        val memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()

        val allReadings = exposureRepository.getAllReadings()
        val stats = exposureRepository.getStats()
        val recentReadings = exposureRepository.getRecentReadings(24)

        val memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryUsed = memoryAfter - memoryBefore

        println("Memory used for operations: ${memoryUsed / 1024}KB")

        // Verify operations completed successfully
        assertEquals("Should retrieve all readings", 500, allReadings.size)
        assertNotNull("Stats should be calculated", stats)
        assertTrue("Should have recent readings", recentReadings.isNotEmpty())

        // Memory usage should be reasonable
        assertTrue("Memory usage should be efficient", memoryUsed < 5 * 1024 * 1024) // Less than 5MB
    }

    @Test
    fun testOptimizedDataRetrieval() = runBlocking {
        // Insert test data with different timestamps
        val now = System.currentTimeMillis()
        val readings = mutableListOf<ExposureReading>()

        // Add readings from different time periods
        for (i in 1..100) {
            val timestamp = now - (i * 60 * 60 * 1000L) // Spread over 100 hours
            readings.add(ExposureReading(
                timestamp = timestamp,
                wifiLevel = (10..50).random().toDouble(),
                sarLevel = (0.5..3.0).random(),
                bluetoothLevel = (5..25).random().toDouble(),
                type = ExposureType.values().random()
            ))
        }

        readings.forEach { exposureRepository.addExposureReading(it) }

        // Test optimized retrieval methods
        val retrievalTests = listOf(
            "All readings" to { exposureRepository.getAllReadings() },
            "Recent readings (24h)" to { exposureRepository.getRecentReadings(24) },
            "WiFi readings" to { exposureRepository.getReadingsByType(ExposureType.WIFI) },
            "Cellular readings" to { exposureRepository.getReadingsByType(ExposureType.CELLULAR) },
            "Bluetooth readings" to { exposureRepository.getReadingsByType(ExposureType.BLUETOOTH) }
        )

        for ((testName, retrievalFunction) in retrievalTests) {
            val time = measureTimeMillis {
                val result = retrievalFunction()
                assertNotNull("$testName should return data", result)
            }
            println("$testName: ${time}ms")
            assertTrue("$testName should be fast", time < 50)
        }
    }

    @Test
    fun testBackgroundPersistenceOptimization() = runBlocking {
        // Test that persistence doesn't block the main thread
        val testReadings = generateTestReadings(50)

        val persistenceTime = measureTimeMillis {
            // Add readings (this triggers persistence)
            testReadings.forEach { exposureRepository.addExposureReading(it) }

            // Immediately try to read data (should not be blocked)
            val immediateRead = exposureRepository.getAllReadings()
            assertTrue("Should be able to read immediately", immediateRead.isNotEmpty())
        }

        println("Persistence time with immediate read: ${persistenceTime}ms")

        // Should complete reasonably fast even with persistence
        assertTrue("Persistence should not block operations", persistenceTime < 200)
    }

    @Test
    fun testDataIndexingOptimization() = runBlocking {
        // Test that data retrieval by type is optimized
        val mixedReadings = mutableListOf<ExposureReading>()

        // Create readings with specific type distribution
        val types = ExposureType.values()
        for (i in 1..200) {
            mixedReadings.add(ExposureReading(
                timestamp = System.currentTimeMillis() + i,
                wifiLevel = (10..50).random().toDouble(),
                sarLevel = (0.5..3.0).random(),
                bluetoothLevel = (5..25).random().toDouble(),
                type = types.random()
            ))
        }

        mixedReadings.forEach { exposureRepository.addExposureReading(it) }

        // Test filtering performance
        val filterTime = measureTimeMillis {
            val wifiReadings = exposureRepository.getReadingsByType(ExposureType.WIFI)
            val cellularReadings = exposureRepository.getReadingsByType(ExposureType.CELLULAR)
            val bluetoothReadings = exposureRepository.getReadingsByType(ExposureType.BLUETOOTH)

            // Verify we have readings of each type
            val totalFiltered = wifiReadings.size + cellularReadings.size + bluetoothReadings.size
            assertTrue("Should have filtered readings", totalFiltered > 0)
        }

        println("Data filtering time: ${filterTime}ms")

        // Filtering should be fast
        assertTrue("Data filtering should be optimized", filterTime < 30)
    }

    private fun generateTestReadings(count: Int): List<ExposureReading> {
        return (1..count).map { i ->
            ExposureReading(
                timestamp = System.currentTimeMillis() + i,
                wifiLevel = (10..50).random().toDouble(),
                sarLevel = (0.5..3.0).random(),
                bluetoothLevel = (5..25).random().toDouble(),
                type = ExposureType.values().random()
            )
        }
    }
}
