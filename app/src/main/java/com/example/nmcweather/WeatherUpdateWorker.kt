package com.example.nmcweather

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nmcweather.net.NmcClient
import com.example.nmcweather.net.QWeatherClient
import com.example.nmcweather.net.WeatherData

/** 每半小时在后台更新当前天气缓存，并同步桌面组件。 */
class WeatherUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured || prefs.backgroundUpdatesPaused) return Result.success()

        return try {
            val weather = if (prefs.useQWeatherMain) {
                fetchQWeather(prefs)
            } else {
                val code = prefs.weatherCode ?: return Result.success()
                NmcClient.fetchWeather(code)
            }
            prefs.saveWidgetWeather(weather.nowTemp, weather.nowInfo)
            // 尚未达到暂停状态时，后台成功一次即打断“连续失败”。
            prefs.resetBackgroundFailures()
            WeatherWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            // 失败只按半小时周期计数，不触发 WorkManager 的额外快速重试。
            if (prefs.recordBackgroundFailure() >= 2) {
                WeatherUpdateScheduler.cancel(applicationContext)
            }
            Result.success()
        }
    }

    private suspend fun fetchQWeather(prefs: Prefs): WeatherData {
        val host = prefs.qHost ?: throw IllegalStateException("和风 Host 为空")
        val key = prefs.qKey ?: throw IllegalStateException("和风 API Key 为空")
        val areaId = prefs.qAreaId
        if (!areaId.isNullOrBlank()) {
            return QWeatherClient.fetchWeather(
                host,
                key,
                areaId,
                prefs.qAreaName ?: prefs.cityName ?: "所选地区"
            )
        }

        val cityName = prefs.cityName ?: throw IllegalStateException("未选择城市")
        val location = QWeatherClient.lookupOne(host, key, cityName)
            ?: throw IllegalStateException("和风 GeoAPI 找不到所选城市")
        prefs.saveCoordinates(cityName, location.lon, location.lat)
        return QWeatherClient.fetchWeather(host, key, location.id, cityName)
    }
}
