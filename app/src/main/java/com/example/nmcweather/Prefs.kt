package com.example.nmcweather

import android.content.Context

/** 简单的本地偏好存储：记住用户选的城市和雷达地区。 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("nmc_weather", Context.MODE_PRIVATE)

    var provinceCode: String?
        get() = sp.getString("provinceCode", null)
        set(v) { sp.edit().putString("provinceCode", v).apply() }

    var cityCode: String?
        get() = sp.getString("cityCode", null)
        set(v) { sp.edit().putString("cityCode", v).apply() }

    var cityName: String?
        get() = sp.getString("cityName", null)
        set(v) { sp.edit().putString("cityName", v).apply() }

    /** 可选的 nmc 区域/气象站；为空时使用城市站点。 */
    var areaCode: String?
        get() = sp.getString("areaCode", null)
        set(v) { sp.edit().putString("areaCode", v).apply() }

    var areaName: String?
        get() = sp.getString("areaName", null)
        set(v) { sp.edit().putString("areaName", v).apply() }

    val weatherCode: String? get() = areaCode ?: cityCode

    val weatherLocationName: String?
        get() = (areaName ?: cityName)?.substringBefore('（')?.trim()

    var regionKey: String
        get() = sp.getString("regionKey", "huanan") ?: "huanan"
        set(v) { sp.edit().putString("regionKey", v).apply() }

    // 和风天气（QWeather）短临降水配置
    var qKey: String?
        get() = sp.getString("qKey", null)
        set(v) { sp.edit().putString("qKey", v).apply() }

    var qHost: String?
        get() = sp.getString("qHost", null)
        set(v) { sp.edit().putString("qHost", v).apply() }

    // 城市坐标缓存（避免每次都调 GeoAPI）
    var coordCity: String?
        get() = sp.getString("coordCity", null)
        set(v) { sp.edit().putString("coordCity", v).apply() }

    var coordLon: String?
        get() = sp.getString("coordLon", null)
        set(v) { sp.edit().putString("coordLon", v).apply() }

    var coordLat: String?
        get() = sp.getString("coordLat", null)
        set(v) { sp.edit().putString("coordLat", v).apply() }

    val hasQWeather: Boolean get() = !qKey.isNullOrBlank() && !qHost.isNullOrBlank()

    val isConfigured: Boolean get() = !cityCode.isNullOrBlank()

    /** 一次提交选择结果，避免连续触发多次磁盘写入。 */
    fun saveSelection(
        province: String?,
        cityCode: String,
        cityName: String,
        areaCode: String?,
        areaName: String?,
        region: String,
        qWeatherKey: String?,
        qWeatherHost: String?
    ) {
        sp.edit()
            .putString("provinceCode", province)
            .putString("cityCode", cityCode)
            .putString("cityName", cityName)
            .putString("areaCode", areaCode)
            .putString("areaName", areaName)
            .putString("regionKey", region)
            .putString("qKey", qWeatherKey)
            .putString("qHost", qWeatherHost)
            .apply()
    }

    /** 批量缓存坐标，城市变化时自动覆盖。 */
    fun saveCoordinates(city: String, lon: String, lat: String) {
        sp.edit()
            .putString("coordCity", city)
            .putString("coordLon", lon)
            .putString("coordLat", lat)
            .apply()
    }
}
