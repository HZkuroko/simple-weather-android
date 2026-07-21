package com.example.nmcweather

import android.content.Context

/** 本地保存城市、天气数据源、两套行政区和雷达选择。 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("nmc_weather", Context.MODE_PRIVATE)

    var provinceCode: String?
        get() = sp.getString("provinceCode", null)
        set(value) { sp.edit().putString("provinceCode", value).apply() }

    var cityCode: String?
        get() = sp.getString("cityCode", null)
        set(value) { sp.edit().putString("cityCode", value).apply() }

    var cityName: String?
        get() = sp.getString("cityName", null)
        set(value) { sp.edit().putString("cityName", value).apply() }

    /** nmc 独立行政区/气象站选择。 */
    var areaCode: String?
        get() = sp.getString("areaCode", null)
        set(value) { sp.edit().putString("areaCode", value).apply() }

    var areaName: String?
        get() = sp.getString("areaName", null)
        set(value) { sp.edit().putString("areaName", value).apply() }

    /** 和风天气独立行政区选择及坐标。 */
    var qAreaId: String?
        get() = sp.getString("qAreaId", null)
        set(value) { sp.edit().putString("qAreaId", value).apply() }

    var qAreaName: String?
        get() = sp.getString("qAreaName", null)
        set(value) { sp.edit().putString("qAreaName", value).apply() }

    var qAreaLon: String?
        get() = sp.getString("qAreaLon", null)
        set(value) { sp.edit().putString("qAreaLon", value).apply() }

    var qAreaLat: String?
        get() = sp.getString("qAreaLat", null)
        set(value) { sp.edit().putString("qAreaLat", value).apply() }

    /** nmc 或 qweather；凭据缺失时保存阶段会回退为 nmc。 */
    var weatherSource: String
        get() = sp.getString("weatherSource", "nmc") ?: "nmc"
        set(value) { sp.edit().putString("weatherSource", value).apply() }

    val weatherCode: String? get() = areaCode ?: cityCode

    val nmcLocationName: String?
        get() = (areaName ?: cityName)?.substringBefore('（')?.trim()

    val weatherLocationName: String?
        get() = if (useQWeatherMain) qAreaName ?: cityName else nmcLocationName

    var regionKey: String
        get() {
            val old = sp.getString("regionKey", "region_huanan") ?: "region_huanan"
            return if (old.startsWith("region_") || old.startsWith("station_")) old else "region_$old"
        }
        set(value) { sp.edit().putString("regionKey", value).apply() }

    var qKey: String?
        get() = sp.getString("qKey", null)
        set(value) { sp.edit().putString("qKey", value).apply() }

    var qHost: String?
        get() = sp.getString("qHost", null)
        set(value) { sp.edit().putString("qHost", value).apply() }

    /** 城市级坐标缓存；选择和风区县时优先使用 qAreaLon/qAreaLat。 */
    var coordCity: String?
        get() = sp.getString("coordCity", null)
        set(value) { sp.edit().putString("coordCity", value).apply() }

    var coordLon: String?
        get() = sp.getString("coordLon", null)
        set(value) { sp.edit().putString("coordLon", value).apply() }

    var coordLat: String?
        get() = sp.getString("coordLat", null)
        set(value) { sp.edit().putString("coordLat", value).apply() }

    val hasQWeather: Boolean get() = !qKey.isNullOrBlank() && !qHost.isNullOrBlank()
    val useQWeatherMain: Boolean get() = weatherSource == "qweather" && hasQWeather
    val isConfigured: Boolean get() = !cityCode.isNullOrBlank()

    /** 桌面组件读取最近一次成功刷新的缓存。 */
    val widgetTemp: String? get() = sp.getString("widgetTemp", null)
    val widgetInfo: String? get() = sp.getString("widgetInfo", null)

    /** 连续两次后台失败后暂停；只能由用户下拉刷新解除。 */
    val backgroundFailureCount: Int get() = sp.getInt("backgroundFailureCount", 0)
    val backgroundUpdatesPaused: Boolean get() = backgroundFailureCount >= 2

    fun recordBackgroundFailure(): Int {
        val next = (backgroundFailureCount + 1).coerceAtMost(2)
        sp.edit().putInt("backgroundFailureCount", next).commit()
        return next
    }

    fun resetBackgroundFailures() {
        sp.edit().putInt("backgroundFailureCount", 0).commit()
    }

    fun saveSelection(
        province: String?,
        cityCode: String,
        cityName: String,
        nmcAreaCode: String?,
        nmcAreaName: String?,
        qWeatherAreaId: String?,
        qWeatherAreaName: String?,
        qWeatherAreaLon: String?,
        qWeatherAreaLat: String?,
        source: String,
        region: String,
        qWeatherKey: String?,
        qWeatherHost: String?
    ) {
        sp.edit()
            .putString("provinceCode", province)
            .putString("cityCode", cityCode)
            .putString("cityName", cityName)
            .putString("areaCode", nmcAreaCode)
            .putString("areaName", nmcAreaName)
            .putString("qAreaId", qWeatherAreaId)
            .putString("qAreaName", qWeatherAreaName)
            .putString("qAreaLon", qWeatherAreaLon)
            .putString("qAreaLat", qWeatherAreaLat)
            .putString("weatherSource", source)
            .putString("regionKey", region)
            .putString("qKey", qWeatherKey)
            .putString("qHost", qWeatherHost)
            .apply()
    }

    fun saveWidgetWeather(temp: String, info: String) {
        sp.edit()
            .putString("widgetTemp", temp)
            .putString("widgetInfo", info)
            .apply()
    }

    fun saveCoordinates(city: String, lon: String, lat: String) {
        sp.edit()
            .putString("coordCity", city)
            .putString("coordLon", lon)
            .putString("coordLat", lat)
            .apply()
    }
}
