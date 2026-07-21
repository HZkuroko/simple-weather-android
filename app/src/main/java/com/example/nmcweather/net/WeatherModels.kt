package com.example.nmcweather.net

data class Province(val code: String, val name: String)

data class City(val code: String, val name: String)

data class QWeatherLocation(
    val id: String,
    val name: String,
    val adm2: String,
    val adm1: String,
    val lat: String,
    val lon: String
)

data class DayForecast(
    val date: String,
    val weekday: String,
    val dayInfo: String,
    val nightInfo: String,
    val high: String,
    val low: String,
    val wind: String
)

data class HourObs(
    val time: String,
    val temp: String,
    val rain: String,
    val humidity: String
)

data class WeatherData(
    val city: String,
    val publishTime: String,
    val nowTemp: String,
    val nowInfo: String,
    val feels: String,
    val humidity: String,
    val windDir: String,
    val windPower: String,
    val pressure: String,
    val aqi: String,
    val daily: List<DayForecast>,
    val hourly: List<HourObs>
)

// ---------------- 短临降水（和风天气） ----------------

data class MinutelyPoint(
    val time: String,
    val precip: Double,
    val type: String
)

data class MinutelyData(
    val summary: String,
    val points: List<MinutelyPoint>
)
