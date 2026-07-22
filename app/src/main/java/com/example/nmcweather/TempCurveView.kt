package com.example.nmcweather

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** 一天的预报数据（用于未来5天气温曲线）。 */
data class DayTemp(
    val top: String,       // 星期，如 今天/周一
    val info: String,      // 天气，如 晴/多云/小雨
    val windDir: String,   // 风向，如 南风
    val windLevel: String, // 风力，如 微风 / 4-5级
    val high: Double?,     // 最高气温，未知为 null
    val low: Double?       // 最低气温，未知为 null
)

/**
 * 未来5天最高/最低气温双曲线（橙色=高温，蓝色=低温），
 * 下方逐列显示天气图标 + 天气 + 风向 + 风力。
 */
class TempCurveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var data: List<DayTemp> = emptyList()
    private val dp = resources.displayMetrics.density

    private val highLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FB8C00"); style = Paint.Style.STROKE
        strokeWidth = 2f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val lowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3"); style = Paint.Style.STROKE
        strokeWidth = 2f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val highDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FB8C00"); style = Paint.Style.FILL
    }
    private val lowDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2196F3"); style = Paint.Style.FILL
    }
    private val valueHighPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EF6C00"); textSize = 12f * dp
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val valueLowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1976D2"); textSize = 12f * dp
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_weekday); textSize = 11f * dp
        textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 16f * dp; textAlign = Paint.Align.CENTER
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_info); textSize = 11f * dp; textAlign = Paint.Align.CENTER
    }
    private val windPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_axis_text); textSize = 10f * dp; textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_hint); textSize = 13f * dp; textAlign = Paint.Align.CENTER
    }

    fun setData(d: List<DayTemp>) {
        data = d
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (data.isEmpty()) {
            canvas.drawText("暂无预报", w / 2f, h / 2f, hintPaint)
            return
        }

        val n = data.size
        val colW = w / n
        fun cx(i: Int) = colW * i + colW / 2f

        val weekdayBase = 14f * dp
        val bandTop = 30f * dp
        val bandBottom = 96f * dp
        val emojiY = bandBottom + 28f * dp
        val infoY = emojiY + 16f * dp
        val windDirY = infoY + 15f * dp
        val windLevelY = windDirY + 13f * dp

        // 单次遍历求范围，避免绘制阶段创建临时 List。
        var minV = Double.POSITIVE_INFINITY
        var maxV = Double.NEGATIVE_INFINITY
        data.forEach { day ->
            day.high?.let { minV = minOf(minV, it); maxV = maxOf(maxV, it) }
            day.low?.let { minV = minOf(minV, it); maxV = maxOf(maxV, it) }
        }
        if (!minV.isFinite() || !maxV.isFinite()) {
            canvas.drawText("暂无气温数据", w / 2f, h / 2f, hintPaint)
            return
        }
        if (maxV == minV) { maxV += 1.0; minV -= 1.0 }
        val pad = (maxV - minV) * 0.18
        val topV = maxV + pad
        val botV = minV - pad
        fun yFor(v: Double): Float =
            (bandBottom - ((v - botV) / (topV - botV)) * (bandBottom - bandTop)).toFloat()

        // 高温连线
        for (i in 0 until n - 1) {
            val a = data[i].high; val b = data[i + 1].high
            if (a != null && b != null) canvas.drawLine(cx(i), yFor(a), cx(i + 1), yFor(b), highLine)
        }
        // 低温连线
        for (i in 0 until n - 1) {
            val a = data[i].low; val b = data[i + 1].low
            if (a != null && b != null) canvas.drawLine(cx(i), yFor(a), cx(i + 1), yFor(b), lowLine)
        }

        for (i in 0 until n) {
            val d0 = data[i]
            val x = cx(i)
            canvas.drawText(d0.top, x, weekdayBase, weekdayPaint)
            d0.high?.let {
                val y = yFor(it)
                canvas.drawCircle(x, y, 3f * dp, highDot)
                canvas.drawText(fmt(it), x, y - 7f * dp, valueHighPaint)
            }
            d0.low?.let {
                val y = yFor(it)
                canvas.drawCircle(x, y, 3f * dp, lowDot)
                canvas.drawText(fmt(it), x, y + 15f * dp, valueLowPaint)
            }
            canvas.drawText(WeatherIconMapper.iconFor(d0.info), x, emojiY, emojiPaint)
            if (d0.info.isNotBlank()) canvas.drawText(d0.info, x, infoY, infoPaint)
            if (d0.windDir.isNotBlank()) canvas.drawText(d0.windDir, x, windDirY, windPaint)
            if (d0.windLevel.isNotBlank()) canvas.drawText(d0.windLevel, x, windLevelY, windPaint)
        }
    }

    private fun fmt(v: Double): String = Math.round(v).toInt().toString() + "°"

}
