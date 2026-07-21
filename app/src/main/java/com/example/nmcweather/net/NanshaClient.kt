package com.example.nmcweather.net

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class NanshaSnapshot(
    val publishTime: String?,
    val temperature: Double?,
    val humidity: Double?,
    val hourRain: Double?,
    val windDir: String?,
    val windPower: String?,
    val weatherInfo: String?,
    val low: Double?,
    val high: Double?,
    val shortForecast: String?
)

/** 南沙区气象局动态页面的区级实况解析与安全合并。 */
object NanshaClient {
    const val PAGE_URL = "http://www.tqyb.com.cn/nansha/"

    fun parseRenderedText(raw: String): NanshaSnapshot {
        val text = raw.replace('\u00a0', ' ').replace("\r", "")
        require(text.contains("南沙")) { "南沙页面内容无效" }
        val current = text.substringBefore("最新消息").take(6_000)

        val publish = Regex("数据更新时间\\s*[:：]?\\s*(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}(?::\\d{2})?)")
            .find(current)?.groupValues?.getOrNull(1)
        validateFreshness(publish)

        val temp = Regex("(-?\\d{1,2}(?:\\.\\d+)?)\\s*[℃°]")
            .findAll(current).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .firstOrNull { it in -20.0..55.0 }
        val humidity = valueAfterLabel(current, "湿度", "%")?.takeIf { it in 0.0..100.0 }
        val rain = valueAfterLabel(current, "小时雨量", "mm")?.takeIf { it in 0.0..500.0 }
        val windDir = Regex("(东北|东南|西北|西南|偏东|偏南|偏西|偏北|东|南|西|北)风")
            .find(current)?.value
        val windPower = Regex("(\\d+(?:\\s*[-~～]\\s*\\d+)?)\\s*级")
            .find(current)?.groupValues?.getOrNull(1)?.replace(" ", "")?.plus("级")

        val forecastText = text.substringAfter("24小时预报", text)
        val range = Regex("(-?\\d{1,2}(?:\\.\\d+)?)\\s*[℃°]\\s*[~～—-]\\s*(-?\\d{1,2}(?:\\.\\d+)?)\\s*[℃°]")
            .find(forecastText)
        val first = range?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        val second = range?.groupValues?.getOrNull(2)?.toDoubleOrNull()
        val low = listOfNotNull(first, second).minOrNull()?.takeIf { it in -20.0..55.0 }
        val high = listOfNotNull(first, second).maxOrNull()?.takeIf { it in -20.0..55.0 }

        val short = Regex("预计未来3小时[^\\n。]{3,160}[。]?")
            .find(text)?.value?.trim()
        val info = short?.let {
            Regex("我区([^，,。；;]{2,24})").find(it)?.groupValues?.getOrNull(1)
        }?.takeIf { it.contains(Regex("晴|云|阴|雨|雾|霾|雪")) }

        require(temp != null && listOf(humidity, rain, windDir).any { it != null }) {
            "南沙动态数据尚未加载"
        }
        return NanshaSnapshot(publish, temp, humidity, rain, windDir, windPower, info, low, high, short)
    }

    fun merge(base: WeatherData, nansha: NanshaSnapshot): WeatherData {
        val daily = base.daily.toMutableList()
        if (daily.isNotEmpty() && (nansha.low != null || nansha.high != null)) {
            val today = daily[0]
            daily[0] = today.copy(
                low = nansha.low?.let(::temp) ?: today.low,
                high = nansha.high?.let(::temp) ?: today.high
            )
        }
        return base.copy(
            city = "南沙区",
            publishTime = nansha.publishTime ?: base.publishTime,
            nowTemp = nansha.temperature?.let(::temp) ?: base.nowTemp,
            nowInfo = nansha.weatherInfo ?: base.nowInfo,
            humidity = nansha.humidity?.let { number(it) + "%" } ?: base.humidity,
            windDir = nansha.windDir ?: base.windDir,
            windPower = nansha.windPower ?: base.windPower,
            daily = daily
        )
    }

    private fun valueAfterLabel(text: String, label: String, unit: String): Double? =
        Regex("${Regex.escape(label)}\\s*[:：]?\\s*(-?\\d+(?:\\.\\d+)?)\\s*${Regex.escape(unit)}", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

    private fun validateFreshness(value: String?) {
        if (value == null) return
        val formatter = if (value.length > 16) {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        } else DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val published = runCatching { LocalDateTime.parse(value, formatter) }.getOrNull() ?: return
        val age = Duration.between(published, LocalDateTime.now()).toHours()
        require(age in -1..6) { "南沙数据发布时间过旧" }
    }

    private fun temp(value: Double) = number(value) + "°"
    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
