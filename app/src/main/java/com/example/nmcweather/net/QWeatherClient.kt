package com.example.nmcweather.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** 和风天气只负责 GeoAPI 定位和分钟级降水。 */
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
        val url = "https://" + normHost(host) + path + "?" + params + "&key=" + enc(key)
        val root = JSONObject(httpGet(url))
        val code = root.optString("code")
        if (code != "200") {
            throw IllegalStateException("和风接口返回 code=${code.ifBlank { "未知" }}")
        }
        return root
    }

    /** 将中央气象局所选城市/区县解析为分钟降水所需坐标。 */
    suspend fun lookupOne(
        host: String,
        key: String,
        name: String,
        adm: String? = null
    ): QWeatherLocation? {
        if (name.isBlank()) return null
        val admPart = adm?.takeIf { it.isNotBlank() && normalizeName(it) != normalizeName(name) }
            ?.let { "&adm=${enc(it)}" }
            .orEmpty()
        val root = getJson(
            host,
            "/geo/v2/city/lookup",
            "location=${enc(name)}$admPart&range=cn&number=10&lang=zh",
            key
        )
        val array = root.optJSONArray("location") ?: return null
        val expectedName = normalizeName(name)
        val expectedAdm = adm?.let(::normalizeName).orEmpty()
        var first: QWeatherLocation? = null
        for (i in 0 until array.length()) {
            val location = parseLocation(array.getJSONObject(i))
            if (first == null) first = location
            val sameName = normalizeName(location.name) == expectedName
            val sameAdm = expectedAdm.isBlank() || normalizeName(location.adm2).contains(expectedAdm)
            if (sameName && sameAdm) return location
        }
        return if (expectedAdm.isBlank()) first else null
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

    private fun normalizeName(value: String): String = value.trim()
        .substringBefore('（')
        .substringBefore('(')
        .removeSuffix("市")
        .removeSuffix("区")
        .removeSuffix("县")
        .trim()

    private fun parseLocation(item: JSONObject) = QWeatherLocation(
        name = item.optString("name"),
        adm2 = item.optString("adm2"),
        lat = item.optString("lat"),
        lon = item.optString("lon")
    )

    private fun trim2(value: String): String =
        value.toDoubleOrNull()?.let { String.format(Locale.US, "%.2f", it) } ?: value
}
