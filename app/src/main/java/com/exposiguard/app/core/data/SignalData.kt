package com.exposiguard.app.data

enum class SignalQuality {
    NONE,
    POOR,
    MODERATE,
    GOOD,
    GREAT
}

data class SignalInfo(
    val name: String,
    val values: List<Float>,
    val currentValue: Float,
    val unit: String?,
    val quality: SignalQuality,
    val maxValue: Float,
    val minValue: Float
)

abstract class SignalMeasure<T>(
    val name: String,
    val extractor: (T) -> Number?,
    val unit: String?,
    val minValue: Float,
    val maxValue: Float
) {
    abstract fun evaluate(value: Float?): SignalQuality
}
