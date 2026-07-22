package com.example.nmcweather

import android.content.Context
import com.example.nmcweather.net.NmcClient
import com.example.nmcweather.net.WeatherData
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 前台与 WorkManager 共用的 NMC 请求入口。
 * - Mutex 防止同一进程内前后台同时请求。
 * - 非强制刷新可复用两分钟内的成功缓存。
 * - 真正的下拉刷新和城市切换始终联网。
 */
object WeatherRepository {
    data class FetchResult(val data: WeatherData, val fromRecentCache: Boolean)

    private const val RECENT_CACHE_MS = 2 * 60 * 1000L
    private val requestMutex = Mutex()
    private var memoryCode: String? = null
    private var memoryData: WeatherData? = null
    private var memorySavedAt: Long = 0L

    suspend fun fetch(context: Context, prefs: Prefs, force: Boolean = false): FetchResult =
        requestMutex.withLock {
            val cache = WeatherCache(context.applicationContext)
            val code = prefs.weatherCode
                ?: throw IllegalStateException("未选择中央气象局城市")
            if (!force) {
                val now = System.currentTimeMillis()
                memoryData?.takeIf {
                    memoryCode == code && now - memorySavedAt in 0 until RECENT_CACHE_MS
                }?.let { return@withLock FetchResult(it, fromRecentCache = true) }

                // 南沙磁盘缓存可能是合并后的区级数据，不能当作番禺 NMC 原始响应复用。
                if (!prefs.isNansha) {
                    cache.loadWeather(prefs.cacheKey)?.takeIf {
                        now - it.savedAt in 0 until RECENT_CACHE_MS
                    }?.let {
                        memoryCode = code
                        memoryData = it.data
                        memorySavedAt = it.savedAt
                        return@withLock FetchResult(it.data, fromRecentCache = true)
                    }
                }
            }

            val data = NmcClient.fetchWeather(code)
            memoryCode = code
            memoryData = data
            memorySavedAt = System.currentTimeMillis()
            if (!prefs.isNansha) cache.saveWeather(prefs.cacheKey, data, memorySavedAt)
            FetchResult(data, fromRecentCache = false)
        }
}
