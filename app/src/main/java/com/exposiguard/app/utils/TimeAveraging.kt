package com.exposiguard.app.utils

import com.exposiguard.app.data.ExposureReading

object TimeAveraging {
    /**
     * Calcula TWA (promedio ponderado por tiempo) con zero-order hold entre lecturas.
     * Limita huecos a [maxGapMs] si se especifica.
     */
    fun timeWeightedAverage(
        readings: List<ExposureReading>,
        startTime: Long,
        endTime: Long,
        valueSelector: (ExposureReading) -> Double,
        maxGapMs: Long? = null
    ): Double {
        if (readings.isEmpty() || endTime <= startTime) return 0.0
        val sorted = readings.sortedBy { it.timestamp }

        var currentTime = startTime
        val initial = sorted.lastOrNull { it.timestamp <= startTime }
        var currentValue = initial?.let(valueSelector) ?: 0.0
        var area = 0.0

        for (r in sorted) {
            if (r.timestamp < startTime) continue
            if (r.timestamp >= endTime) break
            val nextTime = r.timestamp
            val dt = (nextTime - currentTime).coerceAtLeast(0L)
            val effDt = if (maxGapMs != null) kotlin.math.min(dt, maxGapMs) else dt
            if (effDt > 0) area += currentValue * effDt
            currentTime = nextTime
            currentValue = valueSelector(r)
        }

        if (currentTime < endTime) {
            val dt = (endTime - currentTime).coerceAtLeast(0L)
            val effDt = if (maxGapMs != null) kotlin.math.min(dt, maxGapMs) else dt
            if (effDt > 0) area += currentValue * effDt
        }

        val duration = (endTime - startTime).toDouble()
        return if (duration > 0) area / duration else 0.0
    }
}
