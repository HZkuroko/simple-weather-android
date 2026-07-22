package com.example.nmcweather

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.nmcweather.databinding.ActivityMainBinding
import com.example.nmcweather.databinding.DialogCityPickerBinding
import com.example.nmcweather.databinding.ItemHourlyBinding
import com.example.nmcweather.net.City
import com.example.nmcweather.net.MinutelyData
import com.example.nmcweather.net.NanshaClient
import com.example.nmcweather.net.NanshaSnapshot
import com.example.nmcweather.net.NmcClient
import com.example.nmcweather.net.Province
import com.example.nmcweather.net.QWeatherClient
import com.example.nmcweather.net.WeatherData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val KEY_RADAR_WEBVIEW_STATE = "radar_webview_state"
    }

    /** 仅跨配置切换保留轻量 UI 数据，不持有 Activity/View，避免内存泄漏。 */
    private data class RetainedUiState(
        val weather: WeatherData?,
        val weatherSavedAt: Long,
        val nanshaDetails: String?,
        val rain: RainUiState,
        val rainSavedAt: Long,
        val updateStatus: String?
    )

    private sealed class RainUiState {
        object Locked : RainUiState()
        data class Content(val data: MinutelyData) : RainUiState()
        data class Error(val message: String) : RainUiState()
    }

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private lateinit var cache: WeatherCache
    private var foregroundRefreshJob: Job? = null
    private var refreshJob: Job? = null
    private var lastWeather: WeatherData? = null
    private var lastWeatherSavedAt: Long = 0L
    private var lastNanshaDetails: String? = null
    private var rainUiState: RainUiState = RainUiState.Locked
    private var lastRainSavedAt: Long = 0L
    private var lastUpdateStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        cache = WeatherCache(applicationContext)
        // 配置切换会重建 Activity；后台任务只需在真正冷启动时确认一次。
        if (savedInstanceState == null) {
            WeatherUpdateScheduler.schedule(applicationContext)
        }

        setupRadarWebView()
        setupNanshaProbe()
        setupRainActivation()

        b.tvCity.setOnClickListener { showCityPicker() }
        b.swipe.setOnRefreshListener {
            // 只有用户主动下拉，才解除“后台连续失败两次”的暂停状态。
            prefs.resetBackgroundFailures()
            WeatherUpdateScheduler.schedule(applicationContext)
            hideUpdateStatus()
            refreshAll(initial = false, force = true)
        }

        val retained = lastCustomNonConfigurationInstance as? RetainedUiState

        // 冷启动立即更新；配置切换时优先恢复内存数据和 WebView，避免重复联网。
        if (savedInstanceState == null) {
            if (prefs.isConfigured) {
                loadRadar()
                restoreDiskCache()
                refreshAll(initial = true)
            } else {
                showCityPicker()
            }
        } else if (!prefs.isConfigured) {
            showCityPicker()
        } else {
            if (!restoreRadar(savedInstanceState)) loadRadar()
            if (retained?.weather != null) {
                lastWeatherSavedAt = retained.weatherSavedAt
                lastRainSavedAt = retained.rainSavedAt
                bind(retained.weather, updateWidget = false)
                restoreNanshaDetails(retained.nanshaDetails)
                restoreRainState(retained.rain)
                retained.updateStatus?.let(::showUpdateStatus)
            } else {
                // 进程被系统回收后没有内存缓存，只在这种情况下重新请求。
                restoreDiskCache()
                refreshAll(initial = true)
            }
        }
    }

    // ---------------- 天气 ----------------

    private fun refreshAll(initial: Boolean, force: Boolean = false) {
        if (!prefs.isConfigured) {
            b.swipe.isRefreshing = false
            return
        }
        // 定时刷新或连续下拉时不叠加请求；城市改变等 initial 刷新则替换旧任务。
        if (refreshJob?.isActive == true) {
            if (!initial && !force) {
                b.swipe.isRefreshing = false
                return
            }
            refreshJob?.cancel()
        }
        if (!initial) b.webRadar.reload()
        b.swipe.post { b.swipe.isRefreshing = true }
        refreshJob = lifecycleScope.launch {
            val thisJob = coroutineContext[Job]
            try {
                // 主天气与分钟降水彼此隔离：一项失败不会取消另一项。
                val weatherTask = async {
                    suspendResult {
                        WeatherRepository.fetch(applicationContext, prefs, force = force)
                    }
                }
                val rainTask = async { suspendResult { refreshMinutely() } }

                val weatherResult = weatherTask.await()
                weatherResult.onSuccess { fetch ->
                    val base = fetch.data
                    if (prefs.isNansha) {
                        suspendResult { fetchNanshaRendered() }.fold(
                            onSuccess = { snapshot ->
                                bind(NanshaClient.merge(base, snapshot))
                                showNanshaDetails(snapshot)
                            },
                            onFailure = {
                                bind(base, updateWidget = !fetch.fromRecentCache)
                                showNanshaFallback()
                                toast("南沙天气获取失败，已回退到番禺")
                            }
                        )
                    } else {
                        b.tvNanshaDetails.visibility = View.GONE
                        lastNanshaDetails = null
                        bind(base, updateWidget = !fetch.fromRecentCache)
                    }
                    if (fetch.fromRecentCache) hideUpdateStatus()
                }.onFailure {
                    showWeatherError(it.message)
                    toast("天气更新失败：${it.message ?: "网络错误"}")
                }
                rainTask.await().onFailure { showRainError(it.message) }
            } finally {
                if (refreshJob === thisJob) {
                    b.swipe.isRefreshing = false
                    refreshJob = null
                }
            }
        }
    }

    /** runCatching 会吞掉 CancellationException；网络任务取消必须继续向上传播。 */
    private suspend fun <T> suspendResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun bind(d: WeatherData, updateWidget: Boolean = true) {
        lastWeather = d
        val city = prefs.cityName ?: d.city
        val area = prefs.areaName
        val displayName = if (!area.isNullOrBlank() && area != city) "$city · $area" else city
        b.tvCity.text = "$displayName ▾"
        b.tvPublishTime.text = if (d.publishTime.isNotBlank()) "更新于 ${d.publishTime}" else ""
        b.tvNowTemp.text = d.nowTemp
        b.tvNowInfo.text = d.nowInfo
        b.tvNowFeels.text = "体感 ${d.feels}"
        b.tvNowHumidity.text = "湿度 ${d.humidity}"
        b.tvNowWind.text = "风 ${d.windDir} ${d.windPower}".trim()
        b.tvNowPressure.text = "气压 ${d.pressure}"
        b.tvNowAqi.text = "空气 ${d.aqi}"

        // 仅新数据同步桌面组件；配置切换恢复 UI 时不做重复磁盘写入和组件刷新。
        if (updateWidget) {
            lastWeatherSavedAt = System.currentTimeMillis()
            cache.saveWeather(prefs.cacheKey, d, lastWeatherSavedAt)
            prefs.saveWidgetWeather(d.nowTemp, d.nowInfo)
            WeatherWidgetProvider.updateAll(this)
            hideUpdateStatus()
        }

        b.llHourly.removeAllViews()
        for (h in d.hourly) {
            val ib = ItemHourlyBinding.inflate(layoutInflater, b.llHourly, false)
            ib.tvHour.text = h.time
            ib.tvHTemp.text = h.temp
            ib.tvHRain.text = if (h.rain == "-") "—" else "${h.rain}mm"
            ib.tvHHum.text = h.humidity
            b.llHourly.addView(ib.root)
        }

        b.tempCurve.setData(d.daily.map { day ->
            val (windDir, windLevel) = splitWind(day.wind)
            DayTemp(
                top = day.weekday,
                info = safeWeatherInfo(day.dayInfo, day.nightInfo),
                windDir = windDir,
                windLevel = windLevel,
                high = parseTemp(day.high),
                low = parseTemp(day.low)
            )
        })
        b.tempCurve.contentDescription = "未来天气温度、天气和风力趋势"
    }

    private fun showNanshaDetails(snapshot: NanshaSnapshot) {
        val parts = buildList {
            snapshot.hourRain?.let { add("南沙小时雨量 $it mm") }
            snapshot.shortForecast?.let { add(it) }
        }
        val text = parts.joinToString("\n").takeIf { it.isNotBlank() }
        restoreNanshaDetails(text)
    }

    private fun showNanshaFallback() {
        restoreNanshaDetails("南沙天气暂不可用，当前显示番禺补齐数据")
    }

    private fun restoreNanshaDetails(text: String?) {
        lastNanshaDetails = text
        b.tvNanshaDetails.visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        b.tvNanshaDetails.text = text.orEmpty()
    }

    // ---------------- 雷达 ----------------

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupRadarWebView() {
        b.webRadar.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
        }
        b.webRadar.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !isAllowedWebUrl(request.url.toString(), "nmc.cn")
        }
        b.webRadar.setOnTouchListener { view, _ ->
            view.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupNanshaProbe() {
        b.webNanshaProbe.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            safeBrowsingEnabled = true
        }
    }

    /**
     * 南沙官网的实况由 JavaScript 动态写入页面。这里仅在前台刷新时读取
     * 渲染后的纯文本；后台 Worker 始终使用番禺 NMC，避免后台创建 WebView。
     */
    private suspend fun fetchNanshaRendered(): NanshaSnapshot = withTimeout(20_000L) {
        suspendCancellableCoroutine { continuation ->
            val web = b.webNanshaProbe
            var evaluated = false
            web.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    !isAllowedWebUrl(request.url.toString(), "tqyb.com.cn")

                override fun onPageFinished(view: WebView, url: String) {
                    if (evaluated || !continuation.isActive) return
                    evaluated = true
                    view.postDelayed({
                        if (!continuation.isActive) return@postDelayed
                        view.evaluateJavascript("(function(){return document.body ? document.body.innerText : '';})()") { encoded ->
                            if (!continuation.isActive) return@evaluateJavascript
                            try {
                                val text = JSONArray("[$encoded]").getString(0)
                                continuation.resume(NanshaClient.parseRenderedText(text))
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            } finally {
                                // 解析结束后释放隐藏页面资源；下次触发时再按需加载。
                                view.post { runCatching { view.loadUrl("about:blank") } }
                            }
                        }
                    }, 5_000L)
                }
            }
            continuation.invokeOnCancellation { web.stopLoading() }
            web.loadUrl(NanshaClient.PAGE_URL)
        }
    }

    private fun loadRadar() {
        val radar = RadarRegions.item(prefs.regionKey)
        b.tvRadarTitle.text = "雷达图 · ${radar.name}"
        b.webRadar.contentDescription = "中央气象台${radar.name}雷达图"
        b.webRadar.loadUrl(radar.url)
    }

    private fun restoreRadar(savedInstanceState: Bundle): Boolean {
        val radar = RadarRegions.item(prefs.regionKey)
        b.tvRadarTitle.text = "雷达图 · ${radar.name}"
        b.webRadar.contentDescription = "中央气象台${radar.name}雷达图"
        val state = savedInstanceState.getBundle(KEY_RADAR_WEBVIEW_STATE) ?: return false
        return b.webRadar.restoreState(state) != null
    }

    private fun isAllowedWebUrl(url: String, domain: String): Boolean {
        if (url == "about:blank") return true
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        return uri.scheme in setOf("http", "https") && (host == domain || host.endsWith(".$domain"))
    }

    // ---------------- 城市 / 行政区 / 雷达设置 ----------------

    private fun showCityPicker() {
        val db = DialogCityPickerBinding.inflate(layoutInflater)

        // 雷达仅在设置页选择：类型 → 区域，或 类型 → 省份 → 单站。
        val savedRadarKey = RadarRegions.canonicalKey(prefs.regionKey)
        db.spRadarType.adapter = spinnerAdapter(listOf("区域雷达", "单站雷达"))
        db.spRadarRegion.adapter = spinnerAdapter(RadarRegions.regions.map { it.name })
        db.spRadarProvince.adapter = spinnerAdapter(RadarRegions.provinces.map { it.name })
        db.spRadarRegion.setSelection(RadarRegions.regionIndex(savedRadarKey), false)
        val initialRadarProvince = RadarRegions.provinceIndex(savedRadarKey)
        db.spRadarProvince.setSelection(initialRadarProvince, false)

        fun updateRadarStations(provinceIndex: Int, keepSaved: Boolean) {
            val province = RadarRegions.provinces.getOrElse(provinceIndex) { RadarRegions.provinces[0] }
            db.spRadarStation.adapter = spinnerAdapter(province.stations.map { it.name })
            val stationIndex = if (keepSaved) RadarRegions.stationIndex(provinceIndex, savedRadarKey) else 0
            db.spRadarStation.setSelection(stationIndex, false)
        }

        fun updateRadarMode() {
            val stationMode = db.spRadarType.selectedItemPosition == 1
            db.layoutRadarRegion.visibility = if (stationMode) View.GONE else View.VISIBLE
            db.layoutRadarStation.visibility = if (stationMode) View.VISIBLE else View.GONE
        }

        updateRadarStations(initialRadarProvince, keepSaved = true)
        db.spRadarType.setSelection(if (RadarRegions.isStation(savedRadarKey)) 1 else 0, false)
        updateRadarMode()
        db.spRadarType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) = updateRadarMode()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        var firstRadarProvinceCallback = true
        db.spRadarProvince.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val keepSaved = firstRadarProvinceCallback && pos == initialRadarProvince
                firstRadarProvinceCallback = false
                updateRadarStations(pos, keepSaved)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        db.etQKey.setText(prefs.qKey.orEmpty())
        db.etQHost.setText(prefs.qHost.orEmpty())

        var provinces: List<Province> = emptyList()
        var cities: List<City> = emptyList()
        var areas: List<City> = emptyList()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("天气与雷达设置")
            .setView(db.root)
            .setCancelable(prefs.isConfigured)
            .setPositiveButton("确定", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val city = cities.getOrNull(db.spCity.selectedItemPosition)
                    if (city == null) {
                        toast("请先选择城市")
                        return@setOnClickListener
                    }
                    val area = if (db.spNmcArea.selectedItemPosition > 0) {
                        areas.getOrNull(db.spNmcArea.selectedItemPosition - 1)
                    } else null
                    val rainKey = db.etQKey.text.toString().trim().takeIf { it.isNotEmpty() }
                    val rawRainHost = db.etQHost.text.toString().trim().takeIf { it.isNotEmpty() }
                    val rainHost = rawRainHost?.let(QWeatherClient::normalizeHost)
                    if ((rainKey == null) != (rainHost == null)) {
                        toast(if (rawRainHost != null && rainHost == null) {
                            "API Host 格式无效，请填写 HTTPS 主机名，不要包含路径或参数"
                        } else {
                            "降雨预测的 API Key 和 Host 需要同时填写，或同时留空"
                        })
                        return@setOnClickListener
                    }

                    val radarKey = if (db.spRadarType.selectedItemPosition == 1) {
                        val province = RadarRegions.provinces.getOrElse(db.spRadarProvince.selectedItemPosition) {
                            RadarRegions.provinces[0]
                        }
                        province.stations.getOrElse(db.spRadarStation.selectedItemPosition) {
                            province.stations[0]
                        }.id
                    } else {
                        RadarRegions.regions.getOrElse(db.spRadarRegion.selectedItemPosition) {
                            RadarRegions.regions[5]
                        }.id
                    }

                    try {
                        prefs.saveSelection(
                            province = provinces.getOrNull(db.spProvince.selectedItemPosition)?.code,
                            cityCode = city.code,
                            cityName = city.name,
                            areaCode = area?.code,
                            areaName = area?.name,
                            nanshaFallbackCode = if (area?.code == Prefs.NANSHA_AREA_CODE) {
                                areas.firstOrNull { it.name.contains("番禺") }?.code
                            } else null,
                            region = radarKey,
                            rainApiKey = rainKey,
                            rainApiHost = rainHost
                        )
                    } catch (_: Exception) {
                        toast("安全保存 API Key 失败，请重试或暂时留空")
                        return@setOnClickListener
                    }
                    dialog.dismiss()
                    clearWeatherUiForNewLocation()
                    loadRadar()
                    restoreDiskCache()
                    refreshAll(initial = true, force = true)
                }
        }
        dialog.show()

        lifecycleScope.launch {
            try {
                provinces = NmcClient.fetchProvinces()
                db.spProvince.adapter = spinnerAdapter(provinces.map { it.name })
                val initialIndex = provinces.indexOfFirst { it.code == prefs.provinceCode }
                    .let { if (it < 0) 0 else it }
                db.spProvince.setSelection(initialIndex, false)

                var loadedProvinceCode: String? = null
                fun loadProvince(position: Int, keepSaved: Boolean) {
                    val province = provinces.getOrNull(position) ?: return
                    if (province.code == loadedProvinceCode) return
                    loadedProvinceCode = province.code
                    loadCities(province.code, db, if (keepSaved) prefs.cityCode else null) { loaded ->
                        cities = loaded
                        setupAreaPicker(db, loaded, if (keepSaved) prefs.areaCode else null) {
                            areas = it
                        }
                    }
                }

                db.spProvince.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                        val keepSaved = provinces.getOrNull(pos)?.code == prefs.provinceCode
                        loadProvince(pos, keepSaved)
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }
                loadProvince(initialIndex, keepSaved = true)
            } catch (e: Exception) {
                toast("加载省份失败：${e.message ?: "网络错误"}")
            }
        }
    }

    private fun loadCities(
        provinceCode: String,
        db: DialogCityPickerBinding,
        preselectCode: String?,
        onLoaded: (List<City>) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val list = NmcClient.fetchCities(provinceCode)
                db.spCity.adapter = spinnerAdapter(list.map { it.name })
                val selected = list.indexOfFirst { it.code == preselectCode }
                db.spCity.setSelection(if (selected >= 0) selected else 0, false)
                onLoaded(list)
            } catch (e: Exception) {
                toast("加载城市失败：${e.message ?: "网络错误"}")
            }
        }
    }

    private fun setupAreaPicker(
        db: DialogCityPickerBinding,
        stations: List<City>,
        preselectAreaCode: String?,
        onAreasChanged: (List<City>) -> Unit
    ) {
        fun update(cityPosition: Int, areaCode: String?) {
            val selectedCity = stations.getOrNull(cityPosition) ?: return
            val candidates = stations.filter { it.code != selectedCity.code }
            val specialAreas = if (selectedCity.name.contains("广州")) {
                listOf(City(Prefs.NANSHA_AREA_CODE, "南沙区（南沙天气）"))
            } else emptyList()
            val selectableAreas = specialAreas + candidates
            db.spNmcArea.adapter = spinnerAdapter(
                listOf("不选择（使用${selectedCity.name}）") + selectableAreas.map { it.name }
            )
            val areaIndex = selectableAreas.indexOfFirst { it.code == areaCode }
            db.spNmcArea.setSelection(if (areaIndex >= 0) areaIndex + 1 else 0, false)
            onAreasChanged(selectableAreas)
        }

        update(db.spCity.selectedItemPosition.coerceAtLeast(0), preselectAreaCode)
        var firstCityCallback = true
        db.spCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val keepSaved = firstCityCallback && stations.getOrNull(pos)?.code == prefs.cityCode
                firstCityCallback = false
                update(pos, if (keepSaved) preselectAreaCode else null)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    // ---------------- 短临降水（和风天气） ----------------

    private var rainTapCount = 0

    private fun setupRainActivation() {
        b.tvRainTapHint.setOnClickListener {
            if (prefs.hasQWeather) return@setOnClickListener
            val remaining = 3 - ++rainTapCount
            if (remaining <= 0) {
                showRainLocked()
                showCityPicker()
            } else {
                b.tvRainTapHint.text = "再点 $remaining 次启动降雨预测"
            }
        }
    }

    private fun showRainLocked() {
        rainUiState = RainUiState.Locked
        rainTapCount = 0
        b.llRainContent.visibility = View.GONE
        b.tvRainTapHint.visibility = View.VISIBLE
        b.tvRainTapHint.text = "点击3次启动降雨预测"
    }

    private fun showRainContent() {
        b.tvRainTapHint.visibility = View.GONE
        b.llRainContent.visibility = View.VISIBLE
    }

    private fun showRainError(message: String?) {
        val error = message ?: "请检查 Key / API Host / 网络"
        rainUiState = RainUiState.Error(error)
        showRainContent()
        b.tvRainHeadline.text = "短临降水获取失败"
        b.tvRainSummary.text = error
        b.rainChart.setData(emptyList())
        b.rainChart.contentDescription = "短临降水获取失败：$error"
    }

    private suspend fun refreshMinutely() {
        if (!prefs.hasQWeather) {
            showRainLocked()
            return
        }
        showRainContent()
        try {
            val host = prefs.qHost!!
            val key = prefs.qKey!!
            val locationName = prefs.weatherLocationName.orEmpty()
            var lon = prefs.coordLon
            var lat = prefs.coordLat
            if (lon.isNullOrBlank() || lat.isNullOrBlank() || prefs.coordCity != locationName) {
                val location = QWeatherClient.lookupOne(host, key, locationName, prefs.cityName)
                    ?: throw IllegalStateException("和风天气找不到「$locationName」的坐标")
                lon = location.lon
                lat = location.lat
                prefs.saveCoordinates(locationName, lon, lat)
            }
            bindMinutely(QWeatherClient.fetchMinutely(host, key, lon!!, lat!!))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            showRainError(e.message)
        }
    }

    private fun bindMinutely(data: MinutelyData, persist: Boolean = true) {
        if (persist) {
            lastRainSavedAt = System.currentTimeMillis()
            cache.saveRain(prefs.cacheKey, data, lastRainSavedAt)
        }
        rainUiState = RainUiState.Content(data)
        val next30 = data.points.take(6)
        val rainyPoints = next30.count { it.precip > 0.0 }
        val total = next30.sumOf { it.precip }
        b.tvRainHeadline.text = when {
            data.points.isEmpty() -> "暂无分钟级降水数据"
            rainyPoints == 0 -> "未来30分钟：预计无雨"
            else -> {
                val level = when {
                    total >= 3.0 -> "较大"
                    total >= 1.0 -> "中等"
                    else -> "小雨"
                }
                val estimate = Math.round(rainyPoints * 100.0 / next30.size).toInt()
                "未来30分钟：可能有雨（$level，概率约$estimate% · 估算）"
            }
        }
        b.tvRainSummary.text = data.summary
        b.rainChart.setData(data.points.take(12).map { hm(it.time) to it.precip })
        b.rainChart.contentDescription = "${b.tvRainHeadline.text}。${data.summary}"
    }

    private fun restoreRainState(state: RainUiState) {
        when (state) {
            RainUiState.Locked -> showRainLocked()
            is RainUiState.Content -> bindMinutely(state.data, persist = false)
            is RainUiState.Error -> showRainError(state.message)
        }
    }

    private fun hm(value: String): String {
        val t = value.indexOf('T')
        return if (t >= 0 && value.length >= t + 6) value.substring(t + 1, t + 6) else value
    }

    /** 最后一层显示保护，任何数据源的缺失标记都不会直接进入曲线。 */
    private fun safeWeatherInfo(vararg values: String): String = values.firstOrNull { value ->
        val text = value.trim()
        text.isNotEmpty() && text != "-" && text != "--" && text != "9999" &&
            !text.equals("null", ignoreCase = true)
    }?.trim() ?: "暂无"

    private fun parseTemp(value: String?): Double? {
        if (value.isNullOrBlank() || value == "-") return null
        return value.replace("℃", "").replace("°", "").trim().toDoubleOrNull()
    }

    private fun splitWind(value: String): Pair<String, String> {
        val wind = value.trim()
        if (wind.isEmpty()) return "" to ""
        val space = wind.indexOf(' ')
        if (space > 0) return wind.substring(0, space) to wind.substring(space + 1).trim()
        val index = wind.indexOf('风')
        return if (index in 0 until wind.length - 1) {
            wind.substring(0, index + 1) to wind.substring(index + 1)
        } else wind to ""
    }

    private fun spinnerAdapter(items: List<String>) = ArrayAdapter(
        this,
        android.R.layout.simple_spinner_item,
        items
    ).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun restoreDiskCache(): Boolean {
        val cachedWeather = cache.loadWeather(prefs.cacheKey)
        cachedWeather?.let {
            lastWeatherSavedAt = it.savedAt
            bind(it.data, updateWidget = false)
            showUpdateStatus("正在更新，当前显示 ${formatCacheTime(it.savedAt)} 的缓存数据")
        }
        if (prefs.hasQWeather) {
            cache.loadRain(prefs.cacheKey)?.let {
                lastRainSavedAt = it.savedAt
                bindMinutely(it.data, persist = false)
            }
        }
        return cachedWeather != null
    }

    private fun clearWeatherUiForNewLocation() {
        lastWeather = null
        lastWeatherSavedAt = 0L
        lastNanshaDetails = null
        lastRainSavedAt = 0L
        rainUiState = RainUiState.Locked
        b.tvCity.text = "${prefs.cityName.orEmpty()}${prefs.areaName?.let { " · $it" }.orEmpty()} ▾"
        b.tvPublishTime.text = ""
        b.tvNowTemp.text = "--°"
        b.tvNowInfo.text = "--"
        b.tvNowFeels.text = "体感 --"
        b.tvNowHumidity.text = "湿度 --"
        b.tvNowWind.text = "风 --"
        b.tvNowPressure.text = "气压 --"
        b.tvNowAqi.text = "空气 --"
        b.tvNanshaDetails.visibility = View.GONE
        b.llHourly.removeAllViews()
        b.tempCurve.setData(emptyList())
        showRainLocked()
        showUpdateStatus("正在获取新位置天气…")
    }

    private fun showWeatherError(message: String?) {
        val reason = message ?: "网络错误"
        val text = if (lastWeather != null && lastWeatherSavedAt > 0L) {
            "更新失败，正在显示 ${formatCacheTime(lastWeatherSavedAt)} 的缓存数据：$reason"
        } else {
            "天气更新失败：$reason"
        }
        showUpdateStatus(text)
    }

    private fun showUpdateStatus(text: String) {
        lastUpdateStatus = text
        b.tvUpdateStatus.text = text
        b.tvUpdateStatus.visibility = View.VISIBLE
    }

    private fun hideUpdateStatus() {
        lastUpdateStatus = null
        b.tvUpdateStatus.visibility = View.GONE
        b.tvUpdateStatus.text = ""
    }

    private fun formatCacheTime(time: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(time))

    override fun onStart() {
        super.onStart()
        if (prefs.backgroundUpdatesPaused && b.tvUpdateStatus.visibility != View.VISIBLE) {
            showUpdateStatus("后台更新已暂停，下拉刷新可恢复")
        }
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = lifecycleScope.launch {
            while (isActive) {
                delay(30 * 60 * 1000L)
                if (prefs.isConfigured && !b.swipe.isRefreshing) {
                    refreshAll(initial = false)
                }
            }
        }
    }

    override fun onStop() {
        foregroundRefreshJob?.cancel()
        foregroundRefreshJob = null
        super.onStop()
    }

    override fun onPause() {
        super.onPause()
        b.webRadar.onPause()
    }

    override fun onResume() {
        super.onResume()
        b.webRadar.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val radarState = Bundle()
        b.webRadar.saveState(radarState)
        outState.putBundle(KEY_RADAR_WEBVIEW_STATE, radarState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Used intentionally to retain lightweight data across configuration changes")
    override fun onRetainCustomNonConfigurationInstance(): Any = RetainedUiState(
        weather = lastWeather,
        weatherSavedAt = lastWeatherSavedAt,
        nanshaDetails = lastNanshaDetails,
        rain = rainUiState,
        rainSavedAt = lastRainSavedAt,
        updateStatus = lastUpdateStatus
    )

    override fun onDestroy() {
        refreshJob?.cancel()
        refreshJob = null
        b.webRadar.apply {
            loadUrl("about:blank")
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        b.webNanshaProbe.apply {
            loadUrl("about:blank")
            stopLoading()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
