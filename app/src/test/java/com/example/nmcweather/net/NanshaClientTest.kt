package com.example.nmcweather.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NanshaClientTest {
    @Test
    fun parseRenderedText_extractsReasonableLiveValues() {
        val text = """
            南沙天气
            当前气温 28.5℃
            湿度：80%
            小时雨量：0.0mm
            偏南风 2-3级
            预计未来3小时我区多云，局部有阵雨。
        """.trimIndent()

        val result = NanshaClient.parseRenderedText(text)
        assertEquals(28.5, result.temperature!!, 0.001)
        assertEquals(80.0, result.humidity!!, 0.001)
        assertEquals(0.0, result.hourRain!!, 0.001)
        assertNotNull(result.shortForecast)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseRenderedText_rejectsUnrelatedPage() {
        NanshaClient.parseRenderedText("普通网页，没有目标站点数据")
    }
}
