package com.example.nmcweather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * 一个轻量的降水曲线控件（不依赖任何图表库）。
 * 传入一组 (时间标签, 降水量mm)，绘制面积曲线图。
 */
class RainChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var data: List<Pair<String, Double>> = emptyList()
    private val dp = resources.displayMetrics.density

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BDBDBD"); strokeWidth = 1f * dp
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EEEEEE"); strokeWidth = 1f * dp
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3"); style = Paint.Style.STROKE
        strokeWidth = 2f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#332196F3"); style = Paint.Style.FILL
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3"); style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575"); textSize = 10f * dp
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E"); textSize = 13f * dp; textAlign = Paint.Align.CENTER
    }

    fun setData(d: List<Pair<String, Double>>) {
        data = d
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val padL = 34f * dp
        val padR = 12f * dp
        val padT = 10f * dp
        val padB = 22f * dp
        val chartW = w - padL - padR
        val chartH = h - padT - padB
        val bottom = padT + chartH

        if (data.isEmpty()) {
            canvas.drawText("暂无降水数据", w / 2f, h / 2f, hintPaint)
            return
        }

        val maxRaw = data.maxOf { it.second }
        val maxV = max(maxRaw, 1.0) // 至少按 1mm 刻度，避免无雨时曲线失真
        // 固定三条网格线，避免 onDraw 中创建临时集合。
        for (level in 0..2) {
            val lv = maxV * level / 2.0
            val y = bottom - (lv / maxV * chartH).toFloat()
            canvas.drawLine(padL, y, w - padR, y, gridPaint)
            canvas.drawText(fmt(lv), 2f * dp, y + 3f * dp, textPaint)
        }
        canvas.drawLine(padL, bottom, w - padR, bottom, axisPaint)

        val n = data.size
        val stepX = if (n > 1) chartW / (n - 1) else 0f
        fun xAt(i: Int) = padL + stepX * i
        fun yAt(v: Double) = bottom - (v / maxV * chartH).toFloat()

        val fill = Path()
        fill.moveTo(xAt(0), bottom)
        for (i in data.indices) fill.lineTo(xAt(i), yAt(data[i].second))
        fill.lineTo(xAt(n - 1), bottom)
        fill.close()
        canvas.drawPath(fill, fillPaint)

        val line = Path()
        for (i in data.indices) {
            val x = xAt(i)
            val y = yAt(data[i].second)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        canvas.drawPath(line, linePaint)

        for (i in data.indices) {
            canvas.drawCircle(xAt(i), yAt(data[i].second), 2f * dp, dotPaint)
        }

        drawTimeLabel(canvas, 0, n, padL, padR, w, h, stepX)
        if (n > 2) drawTimeLabel(canvas, n / 2, n, padL, padR, w, h, stepX)
        if (n > 1) drawTimeLabel(canvas, n - 1, n, padL, padR, w, h, stepX)
    }

    private fun drawTimeLabel(
        canvas: Canvas,
        index: Int,
        size: Int,
        padL: Float,
        padR: Float,
        width: Float,
        height: Float,
        stepX: Float
    ) {
        if (index !in 0 until size) return
        val label = data[index].first
        val textWidth = textPaint.measureText(label)
        val pointX = padL + stepX * index
        val x = (pointX - textWidth / 2f).coerceIn(padL, width - padR - textWidth)
        canvas.drawText(label, x, height - 6f * dp, textPaint)
    }

    private fun fmt(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else java.lang.String.format(java.util.Locale.US, "%.1f", v)
}
