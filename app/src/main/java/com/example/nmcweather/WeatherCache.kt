package com.example.nmcweather

import android.content.Context
import com.example.nmcweather.net.DayForecast
import com.example.nmcweather.net.HourObs
import com.example.nmcweather.net.MinutelyData
import com.example.nmcweather.net.MinutelyPoint
import com.example.nmcweather.net.WeatherData
import org.json.JSONArray
import org.json.JSONObject

/** 最近一次成功数据的磁盘缓存：离线冷启动可展示，解析失败时自动忽略。 */
class WeatherCache(context: Context) {
    data class CachedWeather(val data: WeatherData, val savedAt: Long)
    data class CachedRain(val data: MinutelyData, val savedAt: Long)

    companion object {
        private const val STORE = "nmc_weather_cache"
        private const val WEATHER_JSON = "weather_json"
        private const val WEATHER_KEY = "weather_key"
        private const val WEATHER_TIME = "weather_time"
        private const val RAIN_JSON = "rain_json"
        private const val RAIN_KEY = "rain_key"
        private const val RAIN_TIME = "rain_time"
    }

    private val sp = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun saveWeather(key: String, data: WeatherData, savedAt: Long = System.currentTimeMillis()) {
        sp.edit()
            .putString(WEATHER_KEY, key)
            .putString(WEATHER_JSON, weatherToJson(data).toString())
            .putLong(WEATHER_TIME, savedAt)
            .apply()
    }

    fun loadWeather(key: String): CachedWeather? = runCatching {
        if (sp.getString(WEATHER_KEY, null) != key) return null
        val raw = sp.getString(WEATHER_JSON, null) ?: return null
        val savedAt = sp.getLong(WEATHER_TIME, 0L).takeIf { it > 0L } ?: return null
        CachedWeather(weatherFromJson(JSONObject(raw)), savedAt)
    }.getOrNull()

    fun saveRain(key: String, data: MinutelyData, savedAt: Long = System.currentTimeMillis()) {
        sp.edit()
            .putString(RAIN_KEY, key)
            .putString(RAIN_JSON, rainToJson(data).toString())
            .putLong(RAIN_TIME, savedAt)
            .apply()
    }

    fun loadRain(key: String): CachedRain? = runCatching {
        if (sp.getString(RAIN_KEY, null) != key) return null
        val raw = sp.getString(RAIN_JSON, null) ?: return null
        val savedAt = sp.getLong(RAIN_TIME, 0L).takeIf { it > 0L } ?: return null
        CachedRain(rainFromJson(JSONObject(raw)), savedAt)
    }.getOrNull()

    fun clear() {
        sp.edit().clear().apply()
    }

    private fun weatherToJson(d: WeatherData) = JSONObject().apply {
        put("city", d.city); put("publishTime", d.publishTime); put("nowTemp", d.nowTemp)
        put("nowInfo", d.nowInfo); put("feels", d.feels); put("humidity", d.humidity)
        put("windDir", d.windDir); put("windPower", d.windPower); put("pressure", d.pressure)
        put("aqi", d.aqi)
        put("daily", JSONArray().apply {
            d.daily.forEach { x -> put(JSONObject().apply {
                put("date", x.date); put("weekday", x.weekday); put("dayInfo", x.dayInfo)
                put("nightInfo", x.nightInfo); put("high", x.high); put("low", x.low); put("wind", x.wind)
            }) }
        })
        put("hourly", JSONArray().apply {
            d.hourly.forEach { x -> put(JSONObject().apply {
                put("time", x.time); put("temp", x.temp); put("rain", x.rain); put("humidity", x.humidity)
            }) }
        })
    }

    private fun weatherFromJson(o: JSONObject): WeatherData {
        val daily = o.getJSONArray("daily").let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { x -> DayForecast(
                date = x.getString("date"), weekday = x.getString("weekday"),
                dayInfo = x.getString("dayInfo"), nightInfo = x.getString("nightInfo"),
                high = x.getString("high"), low = x.getString("low"), wind = x.getString("wind")
            ) } }
        }
        val hourly = o.getJSONArray("hourly").let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { x -> HourObs(
                time = x.getString("time"), temp = x.getString("temp"),
                rain = x.getString("rain"), humidity = x.getString("humidity")
            ) } }
        }
        return WeatherData(
            city = o.getString("city"), publishTime = o.getString("publishTime"),
            nowTemp = o.getString("nowTemp"), nowInfo = o.getString("nowInfo"),
            feels = o.getString("feels"), humidity = o.getString("humidity"),
            windDir = o.getString("windDir"), windPower = o.getString("windPower"),
            pressure = o.getString("pressure"), aqi = o.getString("aqi"),
            daily = daily, hourly = hourly
        )
    }

    private fun rainToJson(d: MinutelyData) = JSONObject().apply {
        put("summary", d.summary)
        put("points", JSONArray().apply {
            d.points.forEach { x -> put(JSONObject().apply {
                put("time", x.time); put("precip", x.precip); put("type", x.type)
            }) }
        })
    }

    private fun rainFromJson(o: JSONObject): MinutelyData {
        val points = o.getJSONArray("points").let { a ->
            List(a.length()) { i -> a.getJSONObject(i).let { x -> MinutelyPoint(
                time = x.getString("time"), precip = x.getDouble("precip"), type = x.getString("type")
            ) } }
        }
        return MinutelyData(o.getString("summary"), points)
    }
}
