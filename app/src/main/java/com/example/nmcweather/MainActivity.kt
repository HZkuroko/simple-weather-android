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
import com.example.nmcweather.net.NmcClient
import com.example.nmcweather.net.Province
import com.example.nmcweather.net.QWeatherClient
import com.example.nmcweather.net.QWeatherLocation
import com.example.nmcweather.net.WeatherData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
                    if (prefs.useQWeatherMain) fetchQWeatherMain()
                    else NmcClient.fetchWeather(
                        prefs.weatherCode ?: throw IllegalStateException("未选择中央气象局城市")
                    )
                }
            }
            val rainTask = async { runCatching { refreshMinutely() } }

            weatherTask.await().fold(
                onSuccess = ::bind,
                onFailure = { toast("天气更新失败：${it.message ?: "网络错误"}") }
            )
            rainTask.await().onFailure { showRainError(it.message) }
            b.swipe.isRefreshing = false
        }
    }

    private suspend fun fetchQWeatherMain(): WeatherData {
        val host = prefs.qHost ?: throw IllegalStateException("和风 Host 为空")
        val key = prefs.qKey ?: throw IllegalStateException("和风 API Key 为空")
        val selectedId = prefs.qAreaId
        if (!selectedId.isNullOrBlank()) {
            return QWeatherClient.fetchWeather(
                host,
                key,
                selectedId,
                prefs.qAreaName ?: prefs.cityName ?: "所选地区"
            )
        }

        val cityName = prefs.cityName ?: throw IllegalStateException("未选择城市")
        val location = QWeatherClient.lookupOne(host, key, cityName)
            ?: throw IllegalStateException("和风 GeoAPI 找不到「$cityName」")
        prefs.saveCoordinates(cityName, location.lon, location.lat)
        return QWeatherClient.fetchWeather(host, key, location.id, cityName)
    }

    private fun bind(d: WeatherData) {
        val city = prefs.cityName ?: d.city
        val area = if (prefs.useQWeatherMain) prefs.qAreaName else prefs.areaName
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

    private fun loadRadar() {
        val radar = RadarRegions.item(prefs.regionKey)
        b.tvRadarTitle.text = "雷达图 · ${radar.name}"
        b.webRadar.loadUrl(radar.url)
    }

    // ---------------- 城市 / 数据源 / 行政区选择 ----------------

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
        if (prefs.weatherSource == "qweather") db.rbQWeather.isChecked = true
        else db.rbNmc.isChecked = true

        var provinces: List<Province> = emptyList()
        var cities: List<City> = emptyList()
        var nmcAreas: List<City> = emptyList()
        var qAreas: List<QWeatherLocation> = emptyList()
        db.spQArea.adapter = spinnerAdapter(listOf("不选择（使用城市级天气）"))

        fun updateSourceVisibility() {
            val useQ = db.rbQWeather.isChecked
            db.layoutQArea.visibility = if (useQ) View.VISIBLE else View.GONE
            db.layoutNmcArea.visibility = if (useQ) View.GONE else View.VISIBLE
        }
        updateSourceVisibility()

        fun clearQAreas() {
            qAreas = emptyList()
            db.spQArea.adapter = spinnerAdapter(listOf("不选择（使用城市级天气）"))
            db.tvQAreaHint.text = "先填写 Key 和 Host，再加载当前城市的和风行政区；保持“不选择”时使用城市级天气。"
        }

        fun loadQAreas() {
            val city = cities.getOrNull(db.spCity.selectedItemPosition)
            if (city == null) {
                toast("请先选择城市")
                return
            }
            val key = db.etQKey.text.toString().trim()
            val host = db.etQHost.text.toString().trim()
            if (key.isBlank() || host.isBlank()) {
                toast("请先填写和风 API Key 与 Host")
                return
            }
            db.btnLoadQAreas.isEnabled = false
            db.btnLoadQAreas.text = "正在加载…"
            db.tvQAreaHint.text = "正在通过和风 GeoAPI 加载 ${city.name} 的行政区…"
            lifecycleScope.launch {
                try {
                    qAreas = QWeatherClient.searchDistricts(host, key, city.name)
                    val labels = listOf("不选择（使用${city.name}）") +
                        qAreas.map { "${it.name}（${it.adm2}）" }
                    db.spQArea.adapter = spinnerAdapter(labels)
                    val saved = qAreas.indexOfFirst { it.id == prefs.qAreaId }
                    if (saved >= 0) db.spQArea.setSelection(saved + 1)
                    db.tvQAreaHint.text = if (qAreas.isEmpty()) {
                        "和风未返回可用区县，可保持“不选择”使用城市级天气。"
                    } else {
                        "已加载 ${qAreas.size} 个和风行政区；下拉框最多显示 5 行，可继续滚动。"
                    }
                } catch (e: Exception) {
                    clearQAreas()
                    db.tvQAreaHint.text = "加载失败：${e.message ?: "请检查权限与网络"}"
                    toast("和风行政区加载失败")
                } finally {
                    db.btnLoadQAreas.isEnabled = true
                    db.btnLoadQAreas.text = "加载 / 刷新和风行政区"
                }
            }
        }

        db.rgWeatherSource.setOnCheckedChangeListener { _, checkedId ->
            updateSourceVisibility()
            if (checkedId == db.rbQWeather.id && qAreas.isEmpty() &&
                db.etQKey.text.isNotBlank() && db.etQHost.text.isNotBlank() && cities.isNotEmpty()
            ) {
                loadQAreas()
            }
        }
        db.btnLoadQAreas.setOnClickListener { loadQAreas() }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("初始设置 / 天气来源")
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
                    val nmcArea = if (db.spNmcArea.selectedItemPosition > 0) {
                        nmcAreas.getOrNull(db.spNmcArea.selectedItemPosition - 1)
                    } else null
                    val qArea = if (db.spQArea.selectedItemPosition > 0) {
                        qAreas.getOrNull(db.spQArea.selectedItemPosition - 1)
                    } else null
                    val qKey = db.etQKey.text.toString().trim().takeIf { it.isNotEmpty() }
                    val qHost = db.etQHost.text.toString().trim().takeIf { it.isNotEmpty() }
                    val requestedQ = db.rbQWeather.isChecked
                    val effectiveSource = if (requestedQ && qKey != null && qHost != null) {
                        "qweather"
                    } else {
                        "nmc"
                    }
                    if (requestedQ && effectiveSource == "nmc") {
                        toast("Key 或 Host 留空，已自动改用中央气象局")
                    }

                    val radarKey = if (db.spRadarType.selectedItemPosition == 1) {
                        val radarProvince = RadarRegions.provinces.getOrElse(db.spRadarProvince.selectedItemPosition) {
                            RadarRegions.provinces[0]
                        }
                        radarProvince.stations.getOrElse(db.spRadarStation.selectedItemPosition) {
                            radarProvince.stations[0]
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
                        nmcAreaCode = nmcArea?.code,
                        nmcAreaName = nmcArea?.name,
                        qWeatherAreaId = qArea?.id,
                        qWeatherAreaName = qArea?.name,
                        qWeatherAreaLon = qArea?.lon,
                        qWeatherAreaLat = qArea?.lat,
                        source = effectiveSource,
                        region = radarKey,
                        qWeatherKey = qKey,
                        qWeatherHost = qHost
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
                val provinceIndex = provinces.indexOfFirst { it.code == prefs.provinceCode }
                    .let { if (it < 0) 0 else it }
                db.spProvince.setSelection(provinceIndex)

                db.spProvince.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                        val keepSaved = provinces.getOrNull(pos)?.code == prefs.provinceCode
                        val province = provinces.getOrNull(pos) ?: return
                        loadCities(province.code, db, if (keepSaved) prefs.cityCode else null) { loaded ->
                            cities = loaded
                            setupAreaPicker(
                                db,
                                loaded,
                                if (keepSaved) prefs.areaCode else null,
                                onAreasChanged = { nmcAreas = it },
                                onCityChanged = { clearQAreas() }
                            )
                            if (keepSaved && db.rbQWeather.isChecked && prefs.hasQWeather) loadQAreas()
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                }

                if (provinces.isNotEmpty()) {
                    loadCities(provinces[provinceIndex].code, db, prefs.cityCode) { loaded ->
                        cities = loaded
                        setupAreaPicker(
                            db,
                            loaded,
                            prefs.areaCode,
                            onAreasChanged = { nmcAreas = it },
                            onCityChanged = { clearQAreas() }
                        )
                        if (db.rbQWeather.isChecked && prefs.hasQWeather) loadQAreas()
                    }
                }
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
                if (selected >= 0) db.spCity.setSelection(selected)
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
        onAreasChanged: (List<City>) -> Unit,
        onCityChanged: () -> Unit
    ) {
        fun update(cityPosition: Int, areaCode: String?) {
            val selectedCity = stations.getOrNull(cityPosition) ?: return
            val candidates = stations.filter { it.code != selectedCity.code }
            db.spNmcArea.adapter = spinnerAdapter(
                listOf("不选择（使用${selectedCity.name}）") + candidates.map { it.name }
            )
            val areaIndex = candidates.indexOfFirst { it.code == areaCode }
            db.spNmcArea.setSelection(if (areaIndex >= 0) areaIndex + 1 else 0)
            onAreasChanged(candidates)
        }

        update(db.spCity.selectedItemPosition.coerceAtLeast(0), preselectAreaCode)
        db.spCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                update(pos, null)
                onCityChanged()
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
            var lon = prefs.qAreaLon
            var lat = prefs.qAreaLat
            if (lon.isNullOrBlank() || lat.isNullOrBlank()) {
                lon = prefs.coordLon
                lat = prefs.coordLat
                if (lon.isNullOrBlank() || lat.isNullOrBlank() || prefs.coordCity != locationName) {
                    val location = QWeatherClient.lookupOne(host, key, locationName)
                        ?: throw IllegalStateException("和风天气找不到「$locationName」的坐标")
                    lon = location.lon
                    lat = location.lat
                    prefs.saveCoordinates(locationName, lon, lat)
                }
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
        super.onDestroy()
    }
}
