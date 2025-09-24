package com.exposiguard.app.repository

import android.content.Context
import android.content.SharedPreferences
import com.exposiguard.app.data.ExposureReading
import com.exposiguard.app.data.ExposureType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import com.exposiguard.app.utils.TimeAveraging
import com.exposiguard.app.utils.AppEvents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ExposureRepository(private val context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("exposure_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val readingsKey = "exposure_readings"

    // Optimización: Mantener una copia en memoria para acceso rápido
    private var cachedReadings: MutableList<ExposureReading> = mutableListOf()

    // Optimización: Índices para búsquedas rápidas
    private val typeIndex: MutableMap<ExposureType, MutableList<ExposureReading>> = ConcurrentHashMap()
    private val timestampIndex: MutableMap<Long, ExposureReading> = ConcurrentHashMap()

    // Optimización: Persistencia diferida
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isDirty = AtomicBoolean(false)
    private val backfillGuard = AtomicBoolean(false)
    private var lastPersistenceTime = 0L
    private val PERSISTENCE_DELAY_MS = 5000L // 5 segundos de delay
    // Aumentar el límite en memoria para evitar recortes frecuentes que puedan sesgar promedios
    // Mantenerlo razonable para SharedPreferences; optimizar si migramos a Room.
    private val MAX_MEMORY_READINGS = 5000 // Límite de memoria

    // Optimización: Estadísticas en caché
    private var cachedStats: Map<String, Any>? = null
    private var statsTimestamp = 0L
    private val STATS_CACHE_DURATION_MS = 30000L // 30 segundos

    init {
        // getString(R.string.comment_load_persisted_data)
        loadPersistedData()
        buildIndices()
    }

    fun addExposureReading(reading: ExposureReading) {
        // Evitar almacenar lecturas sin información (todas las componentes a 0)
        if ((reading.wifiLevel + reading.sarLevel + reading.bluetoothLevel) == 0.0) {
            android.util.Log.d("ExposureRepository", "Skip zero reading (all components 0)")
            return
        }
        synchronized(this) {
            cachedReadings.add(reading)

            // Actualizar índices
            typeIndex.getOrPut(reading.type) { mutableListOf() }.add(reading)
            timestampIndex[reading.timestamp] = reading

            // Invalidar caché de estadísticas
            cachedStats = null

            // Limitar memoria
            if (cachedReadings.size > MAX_MEMORY_READINGS) {
                // Mantener solo las lecturas más recientes
                val toRemove = cachedReadings.size - MAX_MEMORY_READINGS
                cachedReadings = cachedReadings.drop(toRemove).toMutableList()

                // Actualizar índices después de remover
                rebuildIndices()
            }

            // Marcar como sucio para persistencia diferida
            isDirty.set(true)
            schedulePersistence()
        }

        val totalValue = reading.wifiLevel + reading.sarLevel + reading.bluetoothLevel
        android.util.Log.d("ExposureRepository", "Added reading: $totalValue ${reading.type}")
    // Notificar a la app para que otras tabs recalculen
    AppEvents.emit(AppEvents.Event.DataChanged)

        // Intentar backfill diario si aplica (segunda medición del día)
        maybeBackfillDailyGaps()
    }

    fun addExposureReadings(readings: List<ExposureReading>) {
        // Filtrar lecturas sin información (todas las componentes a 0)
        val nonZeroReadings = readings.filter { (it.wifiLevel + it.sarLevel + it.bluetoothLevel) != 0.0 }
        if (nonZeroReadings.isEmpty()) {
            android.util.Log.d("ExposureRepository", "Skip batch: all readings were zero total")
            return
        }
        synchronized(this) {
            cachedReadings.addAll(nonZeroReadings)

            // Actualizar índices por lotes
            nonZeroReadings.forEach { reading ->
                typeIndex.getOrPut(reading.type) { mutableListOf() }.add(reading)
                timestampIndex[reading.timestamp] = reading
            }

            // Invalidar caché y aplicar límites de memoria
            cachedStats = null
            enforceMemoryLimits()

            // Persistencia inmediata para lotes grandes
            if (nonZeroReadings.size > 50) {
                persistData()
            } else {
                isDirty.set(true)
                schedulePersistence()
            }
        }

        android.util.Log.d("ExposureRepository", "Added ${nonZeroReadings.size} readings in batch (filtered from ${readings.size})")
    // Notificar a la app para que otras tabs recalculen
    AppEvents.emit(AppEvents.Event.DataChanged)

        // Intentar backfill diario si aplica (segunda medición del día)
        maybeBackfillDailyGaps()
    }

    fun getAllReadings(): List<ExposureReading> {
        synchronized(this) {
            return cachedReadings.toList()
        }
    }

    fun getReadingsByType(type: ExposureType): List<ExposureReading> {
        // Usar índice para búsqueda rápida
        return typeIndex[type]?.toList() ?: emptyList()
    }

    fun getRecentReadings(hours: Int = 24): List<ExposureReading> {
        val cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000)
        synchronized(this) {
            // Usar búsqueda binaria en lista ordenada por timestamp
            val sortedReadings = cachedReadings.sortedBy { it.timestamp }
            val insertPoint = sortedReadings.binarySearch { it.timestamp.compareTo(cutoffTime) }

            val startIndex = if (insertPoint < 0) -insertPoint - 1 else insertPoint
            return sortedReadings.drop(startIndex)
        }
    }

    fun getReadingsInTimeRange(startTime: Long, endTime: Long): List<ExposureReading> {
        synchronized(this) {
            return cachedReadings.filter { it.timestamp in startTime..endTime }
        }
    }

    fun getStats(): Map<String, Any> {
        val now = System.currentTimeMillis()

        // Retornar estadísticas en caché si son recientes
        cachedStats?.let { stats ->
            if (now - statsTimestamp < STATS_CACHE_DURATION_MS) {
                return stats
            }
        }

        synchronized(this) {
            if (cachedReadings.isEmpty()) {
                val emptyStats = mapOf(
                    "totalReadings" to 0,
                    "averageExposure" to 0.0,
                    "maxExposure" to 0.0,
                    "minExposure" to 0.0,
                    "dataPoints" to 0
                )
                cachedStats = emptyStats
                statsTimestamp = now
                return emptyStats
            }

            // Cálculo optimizado de estadísticas
            val values = cachedReadings.map { it.wifiLevel + it.sarLevel + it.bluetoothLevel }
            val maxTimestamp = cachedReadings.maxOfOrNull { it.timestamp } ?: 0L
            val minTimestamp = cachedReadings.minOfOrNull { it.timestamp } ?: 0L
            val timeSpan = maxTimestamp - minTimestamp

            val stats: MutableMap<String, Any> = mutableMapOf()
            stats["totalReadings"] = cachedReadings.size
            stats["averageExposure"] = values.average()
            stats["maxExposure"] = values.maxOrNull() ?: 0.0
            stats["minExposure"] = values.minOrNull() ?: 0.0
            stats["dataPoints"] = values.size
            stats["timeSpan"] = timeSpan

            cachedStats = stats
            statsTimestamp = now
            return stats
        }
    }

    // --- Time-weighted average helpers ---
    /**
     * Calcula el promedio ponderado por tiempo (TWA) de una serie de lecturas en un rango [startTime, endTime).
     * Usa zero-order hold (el valor se mantiene constante hasta la siguiente lectura). Si no hay lectura previa al
     * inicio, asume 0 hasta la primera lectura dentro del rango. Se puede limitar cada tramo a [maxGapMs] para evitar
     * sesgos cuando hay huecos grandes entre lecturas (por defecto sin límite).
     */
    fun timeWeightedAverage(
        readings: List<com.exposiguard.app.data.ExposureReading>,
        startTime: Long,
        endTime: Long,
        valueSelector: (com.exposiguard.app.data.ExposureReading) -> Double,
        maxGapMs: Long? = null
    ): Double = TimeAveraging.timeWeightedAverage(readings, startTime, endTime, valueSelector, maxGapMs)

    /** TWA del total (wifi + sar + bluetooth) en rango. */
    fun timeWeightedAverageTotal(startTime: Long, endTime: Long, maxGapMs: Long? = null): Double {
        val inRange = getReadingsInTimeRange(startTime, endTime)
        return timeWeightedAverage(inRange, startTime, endTime, { it.wifiLevel + it.sarLevel + it.bluetoothLevel }, maxGapMs)
    }

    /** TWA por componente en rango. */
    fun timeWeightedAverageComponents(
        startTime: Long,
        endTime: Long,
        maxGapMs: Long? = null
    ): Triple<Double, Double, Double> {
        val inRange = getReadingsInTimeRange(startTime, endTime)
        val wifi = timeWeightedAverage(inRange, startTime, endTime, { it.wifiLevel }, maxGapMs)
        val sar = timeWeightedAverage(inRange, startTime, endTime, { it.sarLevel }, maxGapMs)
        val bt = timeWeightedAverage(inRange, startTime, endTime, { it.bluetoothLevel }, maxGapMs)
        return Triple(wifi, sar, bt)
    }

    fun clearOldData(daysToKeep: Int = 7) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)

        synchronized(this) {
            val initialSize = cachedReadings.size
            cachedReadings.removeAll { it.timestamp < cutoffTime }

            // Reconstruir índices después de la limpieza
            rebuildIndices()

            // Invalidar caché
            cachedStats = null

            // Persistir cambios inmediatamente
            persistData()

            android.util.Log.d("ExposureRepository",
                "Cleared old data: $initialSize -> ${cachedReadings.size} readings")
        }
    }

    fun optimizeStorage() {
        synchronized(this) {
            // Compactar datos eliminando duplicados y datos innecesarios
            val uniqueReadings = cachedReadings.distinctBy { it.timestamp }

            if (uniqueReadings.size != cachedReadings.size) {
                cachedReadings = uniqueReadings.toMutableList()
                rebuildIndices()
                cachedStats = null
                persistData()

                android.util.Log.d("ExposureRepository",
                    "Optimized storage: removed ${cachedReadings.size - uniqueReadings.size} duplicates")
            }
        }
    }

    fun getStorageStats(): Map<String, Any> {
        synchronized(this) {
            val jsonSize = gson.toJson(cachedReadings).length
            val memoryUsage = cachedReadings.size * 32L // Estimación aproximada

            return mapOf(
                "totalReadings" to cachedReadings.size,
                "jsonSizeBytes" to jsonSize,
                "estimatedMemoryUsageBytes" to memoryUsage,
                "indexSize" to typeIndex.size + timestampIndex.size,
                "isDirty" to isDirty.get(),
                "lastPersistenceTime" to lastPersistenceTime
            )
        }
    }

    /**
     * Elimina TODAS las lecturas y borra la persistencia.
     * No se invoca automáticamente; útil para limpiar datos heredados de pruebas.
     */
    fun clearAllData() {
        synchronized(this) {
            cachedReadings.clear()
            rebuildIndices()
            cachedStats = null
            sharedPreferences.edit().remove(readingsKey).apply()
            lastPersistenceTime = System.currentTimeMillis()
            android.util.Log.d("ExposureRepository", "All readings cleared by request")
        }
    }

    private fun enforceMemoryLimits() {
        if (cachedReadings.size > MAX_MEMORY_READINGS) {
            // Mantener solo las lecturas más recientes
            cachedReadings = cachedReadings
                .sortedByDescending { it.timestamp }
                .take(MAX_MEMORY_READINGS)
                .toMutableList()

            rebuildIndices()
        }
    }

    private fun buildIndices() {
        synchronized(this) {
            typeIndex.clear()
            timestampIndex.clear()

            cachedReadings.forEach { reading ->
                typeIndex.getOrPut(reading.type) { mutableListOf() }.add(reading)
                timestampIndex[reading.timestamp] = reading
            }
        }
    }

    private fun rebuildIndices() {
        buildIndices()
    }

    private fun schedulePersistence() {
        scope.launch {
            delay(PERSISTENCE_DELAY_MS)

            if (isDirty.getAndSet(false)) {
                persistData()
            }
        }
    }

    private fun persistData() {
        try {
            synchronized(this) {
                val json = gson.toJson(cachedReadings)
                sharedPreferences.edit()
                    .putString(readingsKey, json)
                    .apply()

                lastPersistenceTime = System.currentTimeMillis()
                android.util.Log.d("ExposureRepository", "Persisted ${cachedReadings.size} readings")
            }
        } catch (e: Exception) {
            android.util.Log.e("ExposureRepository", "Error persisting data", e)
            isDirty.set(true) // Reintentar en el próximo ciclo
        }
    }

    /**
     * Rellena huecos horarios del día actual al llegar la segunda medición del día.
     * Regla: a partir de la segunda medición del día (inclusive), calcular el promedio
     * componente a componente de esas dos mediciones y añadir una lectura sintética en
     * cada hora del día (desde las 00:00 hasta la hora actual) donde no exista ninguna
     * lectura, con ese promedio. Se ejecuta una sola vez por día (marcado en prefs).
     */
    private fun maybeBackfillDailyGaps() {
        try {
            // Guard para evitar recursión cuando agregamos lecturas sintéticas
            if (backfillGuard.get()) return

            val bucketMs = 5 * 60 * 1000L // 5 minutos
            fun roundDown(time: Long) = time - (time % bucketMs)

            val calNow = Calendar.getInstance()
            val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calNow.time)
            val initialFlagKey = "initial_backfill_done_$dayKey"
            val untilKey = "backfill_until_$dayKey"

            val startOfDay = (calNow.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val now = System.currentTimeMillis()
            val endBucket = roundDown(now)

            // Lecturas reales de hoy (excluye Backfill), ordenadas
            val realToday = synchronized(this) {
                cachedReadings.asSequence()
                    .filter { it.timestamp in startOfDay..now && it.source != "Backfill" }
                    .sortedBy { it.timestamp }
                    .toList()
            }
            if (realToday.size < 2) return

            val first = realToday[0]
            val second = realToday[1]
            val initialGap = second.timestamp - first.timestamp

            // Buckets ya ocupados (reales o sintéticos)
            val existingBuckets = synchronized(this) {
                cachedReadings.asSequence()
                    .filter { it.timestamp in startOfDay..now }
                    .map { roundDown(it.timestamp) }
                    .toSet()
            }

            var backfillUntil = sharedPreferences.getLong(untilKey, startOfDay - bucketMs)
            if (backfillUntil < startOfDay - bucketMs) backfillUntil = startOfDay - bucketMs

            val synthetic = mutableListOf<ExposureReading>()

            // 1) Backfill inicial si gap >= 1h y aún no se ha hecho
            val oneHourMs = 60 * 60 * 1000L
            val initialDone = sharedPreferences.getBoolean(initialFlagKey, false)
            if (!initialDone && initialGap >= oneHourMs) {
                val avgWifi = listOf(first.wifiLevel, second.wifiLevel).average()
                val avgSar = listOf(first.sarLevel, second.sarLevel).average()
                val avgBt = listOf(first.bluetoothLevel, second.bluetoothLevel).average()

                var t = startOfDay
                while (t <= endBucket) {
                    if (t !in existingBuckets) {
                        synthetic.add(
                            ExposureReading(
                                timestamp = t,
                                wifiLevel = avgWifi,
                                sarLevel = avgSar,
                                bluetoothLevel = avgBt,
                                type = ExposureType.WIFI,
                                source = "Backfill"
                            )
                        )
                    }
                    t += bucketMs
                }
                backfillUntil = endBucket
                sharedPreferences.edit()
                    .putBoolean(initialFlagKey, true)
                    .putLong(untilKey, backfillUntil)
                    .apply()
            } else {
                // 2) Backfill incremental a 5 minutos desde último backfillUntil hasta ahora
                // Usar promedio de la primera y la más reciente lectura real del día
                val firstAndMostRecent = listOf(realToday.first(), realToday.last())
                val avgWifi = firstAndMostRecent.map { it.wifiLevel }.average()
                val avgSar = firstAndMostRecent.map { it.sarLevel }.average()
                val avgBt = firstAndMostRecent.map { it.bluetoothLevel }.average()

                var t = (backfillUntil + bucketMs).coerceAtLeast(startOfDay)
                while (t <= endBucket) {
                    if (t !in existingBuckets) {
                        synthetic.add(
                            ExposureReading(
                                timestamp = t,
                                wifiLevel = avgWifi,
                                sarLevel = avgSar,
                                bluetoothLevel = avgBt,
                                type = ExposureType.WIFI,
                                source = "Backfill"
                            )
                        )
                    }
                    t += bucketMs
                }
                if (endBucket >= startOfDay) {
                    backfillUntil = endBucket
                    sharedPreferences.edit().putLong(untilKey, backfillUntil).apply()
                }
            }

            if (synthetic.isNotEmpty()) {
                android.util.Log.i(
                    "ExposureRepository",
                    "Backfill (5min): añadiendo ${synthetic.size} lecturas sintéticas para $dayKey"
                )
                backfillGuard.set(true)
                try {
                    addExposureReadings(synthetic)
                } finally {
                    backfillGuard.set(false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ExposureRepository", "Falló maybeBackfillDailyGaps: ${e.message}")
        }
    }

    // Exponer un disparador manual para backfill desde UI (debug / herramientas)
    fun triggerBackfillNow() {
        try {
            maybeBackfillDailyGaps()
        } catch (e: Exception) {
            android.util.Log.e("ExposureRepository", "Error triggering backfill manually", e)
        }
    }

    private fun loadPersistedData() {
        try {
            val json = sharedPreferences.getString(readingsKey, null)
            if (json != null) {
                val type = object : TypeToken<List<ExposureReading>>() {}.type
                val persistedReadings: List<ExposureReading> = gson.fromJson(json, type)
                cachedReadings.addAll(persistedReadings)

                android.util.Log.d("ExposureRepository", "Loaded ${persistedReadings.size} persisted readings")
            }
        } catch (e: Exception) {
            android.util.Log.e("ExposureRepository", "Error loading persisted data", e)
            // Limpiar datos corruptos
            sharedPreferences.edit().remove(readingsKey).apply()
        }
    }

    fun shutdown() {
        scope.cancel()
        // Persistencia final antes de cerrar
        if (isDirty.get()) {
            persistData()
        }
    }
}
