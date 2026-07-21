package com.example.nmcweather

/** App 主界面与桌面组件共用同一套天气图标。 */
object WeatherIconMapper {
    fun iconFor(info: String): String = when {
        info.isBlank() || info == "-" || info == "--" || info == "9999" || info == "暂无" -> "—"
        info.contains("雪") -> "❄"
        info.contains("雷") -> "⛈"
        info.contains("雨") -> "🌧"
        info.contains("阴") -> "☁"
        info.contains("多云") -> "⛅"
        info.contains("晴") -> "☀"
        info.contains("雾") || info.contains("霾") -> "🌫"
        else -> "⛅"
    }
}
