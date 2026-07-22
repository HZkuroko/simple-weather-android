package com.example.nmcweather

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/** 后台只更新中央气象局当前天气缓存与桌面组件。 */
class WeatherUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.isConfigured || prefs.backgroundUpdatesPaused) return Result.success()
        if (prefs.weatherCode == null) return Result.success()

        return try {
            val fetch = WeatherRepository.fetch(applicationContext, prefs)
            if (fetch.fromRecentCache) {
                prefs.resetBackgroundFailures()
                return Result.success()
            }
            val weather = fetch.data
            prefs.saveWidgetWeather(weather.nowTemp, weather.nowInfo)
            prefs.resetBackgroundFailures()
            WeatherWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 失败只按半小时周期计数，不触发额外快速重试。
            if (prefs.recordBackgroundFailure() >= 2) {
                WeatherUpdateScheduler.cancel(applicationContext)
            }
            Result.success()
        }
    }
}
