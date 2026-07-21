package com.example.nmcweather

import android.content.Context

/** 本地仅保存中央气象局位置、雷达、降雨预测凭据及组件状态。 */
class Prefs(context: Context) {
    companion object {
        const val NANSHA_AREA_CODE = "special_nansha_tqyb"
    }

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

    var areaCode: String?
        get() = sp.getString("areaCode", null)
        set(value) { sp.edit().putString("areaCode", value).apply() }

    var areaName: String?
        get() = sp.getString("areaName", null)
        set(value) { sp.edit().putString("areaName", value).apply() }

    var nanshaFallbackCode: String?
        get() = sp.getString("nanshaFallbackCode", null)
        set(value) { sp.edit().putString("nanshaFallbackCode", value).apply() }

    val isNansha: Boolean get() = areaCode == NANSHA_AREA_CODE

    /** 南沙网页失败或后台更新时，使用保存的番禺 NMC 站点。 */
    val weatherCode: String?
        get() = if (isNansha) nanshaFallbackCode ?: cityCode else areaCode ?: cityCode

    /** 中央气象局显示位置，同时作为和风分钟降水的地点查询名称。 */
    val weatherLocationName: String?
        get() = if (isNansha) "南沙区" else (areaName ?: cityName)?.substringBefore('（')?.trim()

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

    /** 分钟降水坐标缓存；位置变化时会自动清除。 */
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
    val isConfigured: Boolean get() = !cityCode.isNullOrBlank()

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
        areaCode: String?,
        areaName: String?,
        nanshaFallbackCode: String?,
        region: String,
        rainApiKey: String?,
        rainApiHost: String?
    ) {
        val oldLocation = weatherLocationName
        val newLocation = (areaName ?: cityName).substringBefore('（').trim()
        val editor = sp.edit()
            .putString("provinceCode", province)
            .putString("cityCode", cityCode)
            .putString("cityName", cityName)
            .putString("areaCode", areaCode)
            .putString("areaName", areaName)
            .putString("nanshaFallbackCode", nanshaFallbackCode)
            .putString("regionKey", region)
            .putString("qKey", rainApiKey)
            .putString("qHost", rainApiHost)
            // 清理旧版“和风主天气/和风行政区”配置。
            .remove("weatherSource")
            .remove("qAreaId")
            .remove("qAreaName")
            .remove("qAreaLon")
            .remove("qAreaLat")
        if (oldLocation != newLocation) {
            editor.remove("coordCity").remove("coordLon").remove("coordLat")
        }
        editor.apply()
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
