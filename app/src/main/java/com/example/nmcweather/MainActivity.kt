package com.example.nmcweather

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs
    private var foregroundRefreshJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        WeatherUpdateScheduler.schedule(applicationContext)

        setupRadarWebView()
        setupNanshaProbe()
        setupRainActivation()

        b.tvCity.setOnClickListener { showCityPicker() }
        b.swipe.setOnRefreshListener {
            // 只有用户主动下拉，才解除“后台连续失败两次”的暂停状态。
            prefs.resetBackgroundFailures()
            WeatherUpdateScheduler.schedule(applicationContext)
            refreshAll(initial = false)
        }

        // 冷启动立即更新；此外后台任务约每半小时同步一次桌面组件。
        if (savedInstanceState == null) {
            if (prefs.isConfigured) {
                loadRadar()
                refreshAll(initial = true)
            } else {
                showCityPicker()
            }
        }
    }

    // ---------------- 天气 ----------------

    private fun refreshAll(initial: Boolean) {
        if (!prefs.isConfigured) {
            b.swipe.isRefreshing = false
            return
        }
        if (!initial) b.webRadar.reload()
        b.swipe.post { b.swipe.isRefreshing = true }
        lifecycleScope.launch {
            // 主天气与分钟降水彼此隔离：一项失败不会取消另一项。
            val weatherTask = async {
                runCatching {
                    NmcClient.fetchWeather(
                        prefs.weatherCode ?: throw IllegalStateException("未选择中央气象局城市")
                    )
                }
            }
            val rainTask = async { runCatching { refreshMinutely() } }

            val weatherResult = weatherTask.await()
            weatherResult.onSuccess { base ->
                if (prefs.isNansha) {
                    runCatching { fetchNanshaRendered() }.fold(
                        onSuccess = { snapshot ->
                            bind(NanshaClient.merge(base, snapshot))
                            showNanshaDetails(snapshot)
                        },
                        onFailure = {
                            bind(base)
                            showNanshaFallback()
                            toast("南沙天气获取失败，已回退到番禺")
                        }
                    )
                } else {
                    b.tvNanshaDetails.visibility = View.GONE
                    bind(base)
                }
            }.onFailure { toast("天气更新失败：${it.message ?: "网络错误"}") }
            rainTask.await().onFailure { showRainError(it.message) }
            b.swipe.isRefreshing = false
        }
    }

    private fun bind(d: WeatherData) {
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

        // 主天气成功后同步桌面组件；组件本身不发起网络请求。
        prefs.saveWidgetWeather(d.nowTemp, d.nowInfo)
        WeatherWidgetProvider.updateAll(this)

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
    }

    private fun showNanshaDetails(snapshot: NanshaSnapshot) {
        val parts = buildList {
            snapshot.hourRain?.let { add("南沙小时雨量 $it mm") }
            snapshot.shortForecast?.let { add(it) }
        }
        b.tvNanshaDetails.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
        b.tvNanshaDetails.text = parts.joinToString("\n")
    }

    private fun showNanshaFallback() {
        b.tvNanshaDetails.visibility = View.VISIBLE
        b.tvNanshaDetails.text = "南沙天气暂不可用，当前显示番禺补齐数据"
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
        }
        b.webRadar.webViewClient = WebViewClient()
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
        b.webRadar.loadUrl(radar.url)
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
                    val rainHost = db.etQHost.text.toString().trim().takeIf { it.isNotEmpty() }
                    if ((rainKey == null) != (rainHost == null)) {
                        toast("降雨预测的 API Key 和 Host 需要同时填写，或同时留空")
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
                    dialog.dismiss()
                    loadRadar()
                    refreshAll(initial = true)
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
        showRainContent()
        b.tvRainHeadline.text = "短临降水获取失败"
        b.tvRainSummary.text = message ?: "请检查 Key / API Host / 网络"
        b.rainChart.setData(emptyList())
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
            showRainError(e.message)
        }
    }

    private fun bindMinutely(data: MinutelyData) {
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

    override fun onStart() {
        super.onStart()
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

    override fun onDestroy() {
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
