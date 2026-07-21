package com.example.nmcweather

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nmcweather.net.NmcClient

/** 后台只更新中央气象局当前天气缓存与桌面组件。 */
class WeatherUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured || prefs.backgroundUpdatesPaused) return Result.success()
        val code = prefs.weatherCode ?: return Result.success()

        return try {
            val weather = NmcClient.fetchWeather(code)
            prefs.saveWidgetWeather(weather.nowTemp, weather.nowInfo)
            prefs.resetBackgroundFailures()
            WeatherWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (_: Exception) {
            // 失败只按半小时周期计数，不触发额外快速重试。
            if (prefs.recordBackgroundFailure() >= 2) {
                WeatherUpdateScheduler.cancel(applicationContext)
            }
            Result.success()
        }
    }
}
