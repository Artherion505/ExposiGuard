package com.exposiguard.app.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33444444")
        strokeWidth = 1f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private var data: List<Double> = emptyList()
    private var maxValue: Double = 1.0
    private var labels: List<String> = emptyList()
    private var barRects: MutableList<RectF> = mutableListOf()
    private var selectedIndex: Int = -1
    var onBarSelected: ((Int) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var isDragging = false

    fun setData(values: List<Double>, max: Double? = null) {
        data = values
        maxValue = (max ?: values.maxOrNull() ?: 1.0).coerceAtLeast(1e-6)
        labels = emptyList()
        selectedIndex = -1
        invalidate()
    }

    fun setData(values: List<Double>, labels: List<String>, max: Double? = null) {
        data = values
        this.labels = labels
        maxValue = (max ?: values.maxOrNull() ?: 1.0).coerceAtLeast(1e-6)
        selectedIndex = -1
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val count = data.size
        val labelH = if (labels.isNotEmpty()) (textPaint.fontMetrics.bottom - textPaint.fontMetrics.top) + dp(6f) else 0f
        val chartH = (h - labelH).coerceAtLeast(0f)
        val slot = w / (count.coerceAtLeast(1))
        val barWidth = (slot * 0.7f)
        val gap = (slot * 0.3f)

        // grid baseline
        canvas.drawLine(0f, chartH - 1f, w, chartH - 1f, gridPaint)

        barRects.clear()
        var x = 0f
        for (v in data) {
            val norm = (v / maxValue).coerceIn(0.0, 1.0)
            val barH = (chartH * norm).toFloat()
            // color by level
            paint.color = when {
                norm >= 1.0 -> Color.RED
                norm >= 0.7 -> Color.YELLOW
                else -> Color.parseColor("#4CAF50")
            }
            val left = x + gap * 0.5f
            val top = chartH - barH
            val right = left + barWidth
            val rect = RectF(left, top, right, chartH)
            barRects.add(rect)
            canvas.drawRect(rect, paint)

            // highlight selected
            if (barRects.size - 1 == selectedIndex) {
                highlightPaint.color = when (paint.color) {
                    Color.RED -> Color.argb(200, 200, 0, 0)
                    Color.YELLOW -> Color.argb(200, 200, 200, 0)
                    else -> Color.argb(200, 0, 200, 0)
                }
                canvas.drawRect(rect, highlightPaint)
            }

            x += (barWidth + gap)
        }

        // draw labels
        if (labels.isNotEmpty()) {
            for (i in labels.indices) {
                val rect = barRects.getOrNull(i) ?: continue
                val cx = (rect.left + rect.right) / 2f
                val ly = chartH + (labelH - dp(2f))
                canvas.drawText(labels[i], cx, ly, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (data.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isDragging = false
                // Consumimos el DOWN para poder recibir el UP (tap), pero no bloqueamos scroll si detectamos arrastre
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(event.x - downX)
                val dy = Math.abs(event.y - downY)
                if (!isDragging && dy > touchSlop && dy > dx) {
                    // Ceder el control al padre (NestedScrollView) para scroll vertical
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return false
                }
                // Si no hay arrastre vertical significativo, seguimos esperando posible tap
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    for ((index, rect) in barRects.withIndex()) {
                        if (event.x >= rect.left && event.x <= rect.right) {
                            selectedIndex = index
                            invalidate()
                            onBarSelected?.invoke(index)
                            break
                        }
                    }
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return false
            }
            else -> return false
        }
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
