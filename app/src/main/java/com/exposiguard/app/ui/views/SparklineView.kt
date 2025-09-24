package com.exposiguard.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2
        color = resolveColorPrimary()
    }

    private var data: List<Double> = emptyList()

    fun setData(values: List<Double>) {
        data = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val right = (width - paddingRight).toFloat()
        val bottom = (height - paddingBottom).toFloat()
        val w = (right - left).coerceAtLeast(1f)
        val h = (bottom - top).coerceAtLeast(1f)

        var minV = Double.POSITIVE_INFINITY
        var maxV = Double.NEGATIVE_INFINITY
        for (v in data) {
            minV = min(minV, v)
            maxV = max(maxV, v)
        }
        if (minV == Double.POSITIVE_INFINITY || maxV == Double.NEGATIVE_INFINITY) return

        val range = (maxV - minV).takeIf { it > 1e-9 } ?: 1.0

        path.reset()
        data.forEachIndexed { i, v ->
            val x = left + (i / max(1, data.size - 1).toFloat()) * w
            val norm = (v - minV) / range
            // invert y (0 arriba)
            val y = top + (1f - norm.toFloat()) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, paint)
    }

    private fun resolveColorPrimary(): Int {
        val tv = TypedValue()
        val ok = context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
        return if (ok) tv.data else 0xFF2196F3.toInt() // fallback azul
    }
}
