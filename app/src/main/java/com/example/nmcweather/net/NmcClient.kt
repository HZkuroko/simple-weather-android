package com.example.nmcweather.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate

/**
 * 中国气象网 (nmc.cn) 的非官方 JSON 接口封装。
 * - 省份列表：/rest/province/all
 * - 城市列表：/rest/province/{省code}
 * - 天气数据：/rest/weather?stationid={城市code}
 * 数据中 9999 表示缺失值，统一显示为 "-"。
 */
object NmcClient {
    private const val BASE = "https://www.nmc.cn"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private suspend fun httpGet(urlStr: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", "$BASE/")
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: throw IllegalStateException("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchProvinces(): List<Province> {
        val arr = JSONArray(httpGet("$BASE/rest/province/all"))
        val out = ArrayList<Province>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(Province(o.getString("code"), o.getString("name")))
        }
        return out
    }

    suspend fun fetchCities(provinceCode: String): List<City> {
        val arr = JSONArray(httpGet("$BASE/rest/province/$provinceCode"))
        val out = ArrayList<City>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(City(o.getString("code"), o.optString("city", o.optString("name"))))
        }
        return out
    }

    suspend fun fetchWeather(stationId: String): WeatherData {
        val id = URLEncoder.encode(stationId, "UTF-8")
        val root = JSONObject(httpGet("$BASE/rest/weather?stationid=$id"))
        val data = root.optJSONObject("data")
            ?: throw IllegalStateException("返回数据为空")

        val real = data.getJSONObject("real")
        val station = real.optJSONObject("station")
        val weather = real.getJSONObject("weather")
        val wind = real.optJSONObject("wind")
        val air = data.optJSONObject("air")

        val aqi = air?.let {
            val a = it.optInt("aqi", -1)
            val t = cleanText(it.optString("text", "")).orEmpty()
            if (a >= 0) ("$a " + t).trim() else "-"
        } ?: "-"

        val daily = ArrayList<DayForecast>()
        val detail = data.optJSONObject("predict")?.optJSONArray("detail")
        if (detail != null) {
            val count = minOf(5, detail.length())
            for (i in 0 until count) {
                val d = detail.getJSONObject(i)
                val date = d.optString("date", "")
                val day = d.getJSONObject("day")
                val night = d.getJSONObject("night")
                val dayW = day.getJSONObject("weather")
                val nightW = night.getJSONObject("weather")
                val dayWind = day.optJSONObject("wind")
                val windText = listOf(
                    dayWind?.optString("direct").orEmpty(),
                    dayWind?.optString("power").orEmpty()
                ).mapNotNull(::cleanText)
                    .filter { it != "无持续风向" }
                    .joinToString(" ")
                val rawDayInfo = dayW.optString("info", "")
                val rawNightInfo = nightW.optString("info", "")
                daily.add(
                    DayForecast(
                        date = date,
                        weekday = weekdayOf(date, i),
                        // 当天白天时段结束后，接口可能用 9999 表示缺失；此时改用夜间天气。
                        dayInfo = weatherText(rawDayInfo, rawNightInfo),
                        nightInfo = weatherText(rawNightInfo, rawDayInfo),
                        high = tempStr(dayW.optString("temperature", "")),
                        low = tempStr(nightW.optString("temperature", "")),
                        wind = windText
                    )
                )
            }
        }

        val hourly = ArrayList<HourObs>()
        val passed = data.optJSONArray("passedchart")
        if (passed != null) {
            val tmp = ArrayList<HourObs>()
            val n = minOf(24, passed.length())
            for (i in 0 until n) {
                val h = passed.getJSONObject(i)
                tmp.add(
                    HourObs(
                        time = shortHour(h.optString("time", "")),
                        temp = cleanNum(h.optDouble("temperature", 9999.0), "°"),
                        rain = cleanNum(h.optDouble("rain1h", 9999.0), ""),
                        humidity = cleanNum(h.optDouble("humidity", 9999.0), "%")
                    )
                )
            }
            // passedchart[0] 为最新，反转为时间正序
            hourly.addAll(tmp.asReversed())
        }

        return WeatherData(
            city = station?.optString("city", "-") ?: "-",
            publishTime = real.optString("publish_time", ""),
            nowTemp = cleanNum(weather.optDouble("temperature", 9999.0), "°"),
            nowInfo = weatherText(weather.optString("info", "")),
            feels = cleanNum(weather.optDouble("feelst", 9999.0), "°"),
            humidity = cleanNum(weather.optDouble("humidity", 9999.0), "%"),
            windDir = cleanText(wind?.optString("direct").orEmpty()) ?: "-",
            windPower = cleanText(wind?.optString("power").orEmpty()).orEmpty(),
            pressure = cleanNum(weather.optDouble("airpressure", 9999.0), " hPa"),
            aqi = aqi,
            daily = daily,
            hourly = hourly
        )
    }

    private fun cleanNum(v: Double, unit: String): String =
        if (v.isNaN() || v >= 9999.0) "-"
        else {
            val s = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
            s + unit
        }

    private fun cleanText(value: String): String? {
        val text = value.trim()
        return text.takeUnless {
            it.isBlank() || it == "-" || it == "--" || it == "9999" ||
                it.equals("null", ignoreCase = true)
        }
    }

    private fun weatherText(primary: String, fallback: String = ""): String =
        cleanText(primary) ?: cleanText(fallback) ?: "暂无"

    private fun tempStr(s: String): String {
        val text = s.trim().removeSuffix("°").removeSuffix("℃")
        val number = text.toDoubleOrNull()
        return if (number == null || number.isNaN() || kotlin.math.abs(number) >= 9999.0) "-"
        else "$text°"
    }

    private fun shortHour(t: String): String {
        val parts = t.split(" ")
        val hm = if (parts.size == 2) parts[1] else t
        return if (hm.length >= 5) hm.substring(0, 5) else hm
    }

    private fun weekdayOf(date: String, index: Int): String = try {
        when (LocalDate.parse(date.take(10)).dayOfWeek.value) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; else -> "周日"
        }
    } catch (e: Exception) {
        if (index == 0) "今天" else ""
    }
}
