package com.example.nmcweather

/** 图2红框里的雷达地区 → nmc 页面拼音映射。 */
object RadarRegions {
    val items: List<Pair<String, String>> = listOf(
        "全国" to "chinaall",
        "华北" to "huabei",
        "东北" to "dongbei",
        "华东" to "huadong",
        "华中" to "huazhong",
        "华南" to "huanan",
        "西南" to "xinan",
        "西北" to "xibei"
    )

    fun names(): List<String> = items.map { it.first }

    fun keyAt(index: Int): String = items[index].second

    fun indexOfKey(key: String): Int =
        items.indexOfFirst { it.second == key }.let { if (it < 0) 5 else it }

    fun url(key: String): String = "https://www.nmc.cn/publish/radar/$key.html"
}
