package com.example.nmcweather.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale

/** 和风天气：GeoAPI、区县天气和分钟级降水。 */
object QWeatherClient {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun normHost(value: String): String = value.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun httpGet(urlStr: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, */*")
        }
        try {
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = body.take(180).ifBlank { "无错误正文" }
                throw IllegalStateException("和风 HTTP $status：$detail")
            }
            body.ifBlank { throw IllegalStateException("和风接口返回空内容") }
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun getJson(host: String, path: String, params: String, key: String): JSONObject {
        val url = "https://${normHost(host)}$path?$params&key=${enc(key)}"
        val root = JSONObject(httpGet(url))
        val code = root.optString("code")
        if (code != "200") {
            throw IllegalStateException("和风接口返回 code=${code.ifBlank { "未知" }}")
        }
        return root
    }

    suspend fun lookupOne(host: String, key: String, name: String, adm: String? = null): QWeatherLocation? {
        if (name.isBlank()) return null
        val admPart = adm?.takeIf { it.isNotBlank() }?.let { "&adm=${enc(it)}" }.orEmpty()
        val root = getJson(
            host,
            "/geo/v2/city/lookup",
            "location=${enc(name)}$admPart&range=cn&number=1&lang=zh",
            key
        )
        return root.optJSONArray("location")?.let { if (it.length() > 0) parseLocation(it.getJSONObject(0)) else null }
    }

    suspend fun geoLookup(host: String, key: String, cityName: String): Pair<String, String>? =
        lookupOne(host, key, cityName)?.let { it.lon to it.lat }

    /**
     * GeoAPI 没有“列出全部下级行政区”端点，因此使用“区/县/旗”关键词并用 adm 限定城市，
     * 合并去重后作为和风独立行政区列表。
     */
    suspend fun searchDistricts(host: String, key: String, cityName: String): List<QWeatherLocation> {
        val result = LinkedHashMap<String, QWeatherLocation>()
        for (word in listOf("区", "县", "旗")) {
            val root = getJson(
                host,
                "/geo/v2/city/lookup",
                "location=${enc(word)}&adm=${enc(cityName)}&range=cn&number=20&lang=zh",
                key
            )
            val array = root.optJSONArray("location") ?: continue
            for (i in 0 until array.length()) {
                val location = parseLocation(array.getJSONObject(i))
                if (location.name != cityName && location.adm2.contains(cityName.removeSuffix("市"))) {
                    result[location.id] = location
                }
            }
        }
        return result.values.sortedBy { it.name }
    }

    suspend fun fetchWeather(
        host: String,
        key: String,
        location: String,
        displayName: String
    ): WeatherData = coroutineScope {
        val loc = enc(location)
        val nowTask = async { getJson(host, "/v7/weather/now", "location=$loc&lang=zh", key) }
        val hourTask = async { getJson(host, "/v7/weather/24h", "location=$loc&lang=zh", key) }
        val dayTask = async { getJson(host, "/v7/weather/7d", "location=$loc&lang=zh", key) }

        val nowRoot = nowTask.await()
        val hourRoot = hourTask.await()
        val dayRoot = dayTask.await()
        val now = nowRoot.getJSONObject("now")

        val hourly = ArrayList<HourObs>()
        val hourArray = hourRoot.optJSONArray("hourly") ?: JSONArray()
        for (i in 0 until minOf(24, hourArray.length())) {
            val item = hourArray.getJSONObject(i)
            hourly += HourObs(
                time = shortTime(item.optString("fxTime")),
                temp = valueWithUnit(item.optString("temp"), "°"),
                rain = item.optString("precip", "-"),
                humidity = valueWithUnit(item.optString("humidity"), "%")
            )
        }

        val daily = ArrayList<DayForecast>()
        val dayArray = dayRoot.optJSONArray("daily") ?: JSONArray()
        for (i in 0 until minOf(5, dayArray.length())) {
            val item = dayArray.getJSONObject(i)
            val date = item.optString("fxDate")
            daily += DayForecast(
                date = date,
                weekday = weekdayOf(date, i),
                dayInfo = item.optString("textDay", "-"),
                nightInfo = item.optString("textNight", "-"),
                high = valueWithUnit(item.optString("tempMax"), "°"),
                low = valueWithUnit(item.optString("tempMin"), "°"),
                wind = listOf(
                    item.optString("windDirDay"),
                    valueWithUnit(item.optString("windScaleDay"), "级")
                ).filter { it.isNotBlank() && it != "-" }.joinToString(" ")
            )
        }

        WeatherData(
            city = displayName,
            publishTime = formatUpdateTime(nowRoot.optString("updateTime")),
            nowTemp = valueWithUnit(now.optString("temp"), "°"),
            nowInfo = now.optString("text", "-"),
            feels = valueWithUnit(now.optString("feelsLike"), "°"),
            humidity = valueWithUnit(now.optString("humidity"), "%"),
            windDir = now.optString("windDir", "-"),
            windPower = valueWithUnit(now.optString("windScale"), "级"),
            pressure = valueWithUnit(now.optString("pressure"), " hPa"),
            aqi = "-",
            daily = daily,
            hourly = hourly
        )
    }

    suspend fun fetchMinutely(host: String, key: String, lon: String, lat: String): MinutelyData {
        val location = enc("${trim2(lon)},${trim2(lat)}")
        val root = getJson(host, "/v7/minutely/5m", "location=$location&lang=zh", key)
        val array = root.optJSONArray("minutely") ?: JSONArray()
        val points = ArrayList<MinutelyPoint>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            points += MinutelyPoint(
                time = item.optString("fxTime"),
                precip = item.optString("precip", "0").toDoubleOrNull() ?: 0.0,
                type = item.optString("type", "rain")
            )
        }
        return MinutelyData(root.optString("summary"), points)
    }

    private fun parseLocation(item: JSONObject) = QWeatherLocation(
        id = item.optString("id"),
        name = item.optString("name"),
        adm2 = item.optString("adm2"),
        adm1 = item.optString("adm1"),
        lat = item.optString("lat"),
        lon = item.optString("lon")
    )

    private fun trim2(value: String): String =
        value.toDoubleOrNull()?.let { String.format(Locale.US, "%.2f", it) } ?: value

    private fun valueWithUnit(value: String?, unit: String): String =
        value?.takeIf { it.isNotBlank() && it != "9999" }?.plus(unit) ?: "-"

    private fun shortTime(value: String): String {
        val t = value.indexOf('T')
        return if (t >= 0 && value.length >= t + 6) value.substring(t + 1, t + 6) else value
    }

    private fun formatUpdateTime(value: String): String =
        if (value.length >= 16) value.substring(0, 16).replace('T', ' ') else value

    private fun weekdayOf(date: String, index: Int): String = try {
        when (LocalDate.parse(date.take(10)).dayOfWeek.value) {
            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
            5 -> "周五"; 6 -> "周六"; else -> "周日"
        }
    } catch (_: Exception) {
        if (index == 0) "今天" else ""
    }
}
