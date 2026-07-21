package com.example.nmcweather.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * 和风天气 (QWeather) 接口封装，仅用于“短临降水”功能。
 * - GeoAPI 城市查询：/geo/v2/city/lookup  （中文城市名 -> 经纬度）
 * - 分钟级降水：/v7/minutely/5m           （未来2小时每5分钟降水量）
 * 认证：使用 API KEY（作为 key 查询参数）。host 为账号专属 API Host。
 */
object QWeatherClient {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private fun normHost(h: String): String =
        h.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private suspend fun httpGet(urlStr: String): String = withContext(Dispatchers.IO) {
        // 不手动设置 Accept-Encoding：HttpURLConnection 会自动处理 gzip 并透明解压
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json, */*")
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

    /** 中文城市名 -> (经度, 纬度)，均为字符串。找不到返回 null。 */
    suspend fun geoLookup(host: String, key: String, cityName: String): Pair<String, String>? {
        if (cityName.isBlank()) return null
        val h = normHost(host)
        val url = "https://" + h + "/geo/v2/city/lookup?location=" + enc(cityName) + "&number=1&key=" + enc(key)
        val root = JSONObject(httpGet(url))
        val code = root.optString("code")
        if (code != "200") {
            if (code == "404") return null
            throw IllegalStateException("和风 GeoAPI 返回 code=$code")
        }
        val loc = root.optJSONArray("location") ?: return null
        if (loc.length() == 0) return null
        val o = loc.getJSONObject(0)
        val lon = o.optString("lon", "")
        val lat = o.optString("lat", "")
        if (lon.isBlank() || lat.isBlank()) return null
        return lon to lat
    }

    /** 未来2小时每5分钟降水（我们只用前1小时）。 */
    suspend fun fetchMinutely(host: String, key: String, lon: String, lat: String): MinutelyData {
        val h = normHost(host)
        val loc = trim2(lon) + "," + trim2(lat)
        val url = "https://" + h + "/v7/minutely/5m?location=" + enc(loc) + "&lang=zh&key=" + enc(key)
        val root = JSONObject(httpGet(url))
        val code = root.optString("code")
        if (code != "200") throw IllegalStateException("和风分钟级降水返回 code=$code")
        val summary = root.optString("summary", "")
        val arr = root.optJSONArray("minutely")
        val pts = ArrayList<MinutelyPoint>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                pts.add(
                    MinutelyPoint(
                        time = o.optString("fxTime", ""),
                        precip = o.optString("precip", "0").toDoubleOrNull() ?: 0.0,
                        type = o.optString("type", "rain")
                    )
                )
            }
        }
        return MinutelyData(summary, pts)
    }

    /** 经纬度最多保留两位小数（和风要求）。 */
    private fun trim2(s: String): String {
        val d = s.toDoubleOrNull() ?: return s
        return String.format(Locale.US, "%.2f", d)
    }
}
