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
import com.example.nmcweather.net.WeatherData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)

        setupRadarWebView()
        setupRegionSpinner()
        setupRainActivation()

        b.tvCity.setOnClickListener { showCityPicker() }
        // 仅下拉刷新时更新一次
        b.swipe.setOnRefreshListener { refreshAll(initial = false) }

        // 仅冷启动（首次进入 / 重新打开）时更新一次，之后完全静默
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
        val code = prefs.weatherCode
        if (code.isNullOrBlank()) {
            b.swipe.isRefreshing = false
            return
        }
        if (!initial) b.webRadar.reload()
        b.swipe.post { b.swipe.isRefreshing = true }
        lifecycleScope.launch {
            // 两个数据源互不依赖，并行请求；单个接口失败不会取消另一个。
            val weatherTask = async { runCatching { NmcClient.fetchWeather(code) } }
            val rainTask = async { runCatching { refreshMinutely() } }
            weatherTask.await().fold(
                onSuccess = ::bind,
                onFailure = { toast("更新失败：${it.message ?: "网络错误"}") }
            )
            rainTask.await().onFailure { showRainError(it.message) }
            b.swipe.isRefreshing = false
        }
    }

    private fun bind(d: WeatherData) {
        val city = prefs.cityName ?: d.city
        val area = prefs.areaName
        val displayName = if (!area.isNullOrBlank() && area != city) "$city · $area" else city
        b.tvCity.text = "$displayName ▾"
        b.tvPublishTime.text = if (d.publishTime.isNotBlank()) "更新于 " + d.publishTime else ""
        b.tvNowTemp.text = d.nowTemp
        b.tvNowInfo.text = d.nowInfo
        b.tvNowFeels.text = "体感 " + d.feels
        b.tvNowHumidity.text = "湿度 " + d.humidity
        b.tvNowWind.text = ("风 " + d.windDir + " " + d.windPower).trim()
        b.tvNowPressure.text = "气压 " + d.pressure
        b.tvNowAqi.text = "空气 " + d.aqi

        b.llHourly.removeAllViews()
        for (h in d.hourly) {
            val ib = ItemHourlyBinding.inflate(layoutInflater, b.llHourly, false)
            ib.tvHour.text = h.time
            ib.tvHTemp.text = h.temp
            ib.tvHRain.text = if (h.rain == "-") "—" else h.rain + "mm"
            ib.tvHHum.text = h.humidity
            b.llHourly.addView(ib.root)
        }

        val dayTemps = d.daily.map { day ->
            val (wd, wl) = splitWind(day.wind)
            DayTemp(
                top = day.weekday,
                info = if (day.dayInfo.isNotBlank()) day.dayInfo else day.nightInfo,
                windDir = wd,
                windLevel = wl,
                high = parseTemp(day.high),
                low = parseTemp(day.low)
            )
        }
        b.tempCurve.setData(dayTemps)
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
        // 在滚动容器内让 WebView 可以独立拖动/缩放
        b.webRadar.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private fun loadRadar() {
        b.webRadar.loadUrl(RadarRegions.url(prefs.regionKey))
    }

    private fun setupRegionSpinner() {
        b.spRegion.adapter = spinnerAdapter(RadarRegions.names())
        b.spRegion.setSelection(RadarRegions.indexOfKey(prefs.regionKey), false)
        b.spRegion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val key = RadarRegions.keyAt(pos)
                if (key != prefs.regionKey) {
                    prefs.regionKey = key
                    loadRadar()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // ---------------- 城市 / 地区选择 ----------------

    private fun showCityPicker() {
        val db = DialogCityPickerBinding.inflate(layoutInflater)

        db.spDialogRegion.adapter = spinnerAdapter(RadarRegions.names())
        db.spDialogRegion.setSelection(RadarRegions.indexOfKey(prefs.regionKey))

        db.etQKey.setText(prefs.qKey ?: "")
        db.etQHost.setText(prefs.qHost ?: "")

        var provinces: List<Province> = emptyList()
        var cities: List<City> = emptyList()
        var areas: List<City> = emptyList()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("选择城市 / 可选区域")
            .setView(db.root)
            .setCancelable(prefs.isConfigured)
            .setPositiveButton("确定", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val cIdx = db.spCity.selectedItemPosition
                    if (cities.isEmpty() || cIdx < 0 || cIdx >= cities.size) {
                        toast("请先选择城市")
                        return@setOnClickListener
                    }
                    val city = cities[cIdx]
                    val areaPos = db.spArea.selectedItemPosition
                    val area = if (areaPos > 0) areas.getOrNull(areaPos - 1) else null
                    val province = provinces.getOrNull(db.spProvince.selectedItemPosition)
                    val regionKey = RadarRegions.keyAt(db.spDialogRegion.selectedItemPosition)
                    val qKey = db.etQKey.text.toString().trim().takeIf { it.isNotEmpty() }
                    val qHost = db.etQHost.text.toString().trim().takeIf { it.isNotEmpty() }
                    prefs.saveSelection(
                        province = province?.code,
                        cityCode = city.code,
                        cityName = city.name,
                        areaCode = area?.code,
                        areaName = area?.name,
                        region = regionKey,
                        qWeatherKey = qKey,
                        qWeatherHost = qHost
                    )
                    b.spRegion.setSelection(RadarRegions.indexOfKey(regionKey), false)
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
                val pIdx = provinces.indexOfFirst { it.code == prefs.provinceCode }
                    .let { if (it < 0) 0 else it }
                db.spProvince.setSelection(pIdx)
                db.spProvince.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                            val keepSaved = provinces[pos].code == prefs.provinceCode
                            loadCities(
                                provinces[pos].code,
                                db,
                                if (keepSaved) prefs.cityCode else null
                            ) { loaded ->
                                cities = loaded
                                setupAreaPicker(
                                    db,
                                    loaded,
                                    if (keepSaved) prefs.areaCode else null
                                ) { areas = it }
                            }
                        }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
                if (provinces.isNotEmpty()) {
                    loadCities(provinces[pIdx].code, db, prefs.cityCode) { loaded ->
                        cities = loaded
                        setupAreaPicker(db, loaded, prefs.areaCode) { areas = it }
                    }
                }
            } catch (e: Exception) {
                toast("加载省份失败：" + (e.message ?: "网络错误"))
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
                if (preselectCode != null) {
                    val ci = list.indexOfFirst { it.code == preselectCode }
                    if (ci >= 0) db.spCity.setSelection(ci)
                }
                onLoaded(list)
            } catch (e: Exception) {
                toast("加载城市失败：" + (e.message ?: "网络错误"))
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
            // nmc 返回的是省内实际气象站列表；排除当前城市站，其余供用户自行选最近区域。
            val candidates = stations.filter { it.code != selectedCity.code }
            val labels = listOf("不选择（使用${selectedCity.name}）") + candidates.map { it.name }
            db.spArea.adapter = spinnerAdapter(labels)
            val areaIndex = candidates.indexOfFirst { it.code == areaCode }
            db.spArea.setSelection(if (areaIndex >= 0) areaIndex + 1 else 0)
            onAreasChanged(candidates)
        }

        update(db.spCity.selectedItemPosition.coerceAtLeast(0), preselectAreaCode)
        db.spCity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                update(pos, null)
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
            val locationName = prefs.weatherLocationName ?: ""
            var lon = prefs.coordLon
            var lat = prefs.coordLat
            if (lon.isNullOrBlank() || lat.isNullOrBlank() || prefs.coordCity != locationName) {
                val coord = QWeatherClient.geoLookup(host, key, locationName)
                    ?: throw IllegalStateException("和风天气找不到「$locationName」的坐标")
                lon = coord.first
                lat = coord.second
                prefs.saveCoordinates(locationName, coord.first, coord.second)
            }
            val m = QWeatherClient.fetchMinutely(host, key, lon!!, lat!!)
            bindMinutely(m)
        } catch (e: Exception) {
            showRainError(e.message)
        }
    }

    private fun bindMinutely(m: MinutelyData) {
        val next30 = m.points.take(6)
        val hits = next30.count { it.precip > 0.0 }
        val sum30 = next30.sumOf { it.precip }
        val headline = when {
            m.points.isEmpty() -> "暂无分钟级降水数据"
            hits == 0 -> "未来30分钟：预计无雨"
            else -> {
                val level = when {
                    sum30 >= 3.0 -> "较大"
                    sum30 >= 1.0 -> "中等"
                    else -> "小雨"
                }
                val est = Math.round(hits * 100.0 / next30.size).toInt()
                "未来30分钟：可能有雨（$level，概率约${est}% · 估算）"
            }
        }
        b.tvRainHeadline.text = headline
        b.tvRainSummary.text = m.summary
        val hour = m.points.take(12).map { hm(it.time) to it.precip }
        b.rainChart.setData(hour)
    }

    private fun hm(fx: String): String {
        val t = fx.indexOf('T')
        return if (t >= 0 && fx.length >= t + 6) fx.substring(t + 1, t + 6) else fx
    }

    private fun parseTemp(s: String?): Double? {
        if (s.isNullOrBlank() || s == "-") return null
        val cleaned = s.replace("℃", "").replace("°", "").trim()
        return cleaned.toDoubleOrNull()
    }

    private fun splitWind(wind: String): Pair<String, String> {
        val w = wind.trim()
        if (w.isEmpty()) return "" to ""
        val sp = w.indexOf(' ')
        if (sp > 0) return w.substring(0, sp) to w.substring(sp + 1).trim()
        val idx = w.indexOf('风')
        return if (idx in 0 until w.length - 1)
            w.substring(0, idx + 1) to w.substring(idx + 1)
        else w to ""
    }

    private fun spinnerAdapter(items: List<String>) = ArrayAdapter(
        this, android.R.layout.simple_spinner_item, items
    ).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------- WebView 生命周期 ----------------

    override fun onPause() {
        super.onPause()
        b.webRadar.onPause()
    }

    override fun onResume() {
        super.onResume()
        b.webRadar.onResume()
    }

    override fun onDestroy() {
        b.webRadar.destroy()
        super.onDestroy()
    }
}
