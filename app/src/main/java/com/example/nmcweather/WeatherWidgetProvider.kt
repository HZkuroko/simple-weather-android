package com.example.nmcweather

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * 透明的 2×1 桌面天气组件。
 * 显示冷启动、下拉刷新或半小时后台任务最近一次成功取得的天气。
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherUpdateScheduler.schedule(context.applicationContext)
        updateAll(context)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeatherWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val prefs = Prefs(context)
            val views = RemoteViews(context.packageName, R.layout.widget_weather)
            val hasWeather = !prefs.widgetTemp.isNullOrBlank() && !prefs.widgetInfo.isNullOrBlank()

            views.setTextViewText(
                R.id.tvWidgetIcon,
                if (hasWeather) WeatherIconMapper.iconFor(prefs.widgetInfo.orEmpty()) else "⛅"
            )
            views.setTextViewText(R.id.tvWidgetTemp, prefs.widgetTemp ?: "--°")
            views.setTextViewText(
                R.id.tvWidgetInfo,
                if (hasWeather) prefs.widgetInfo else "打开 App 更新"
            )

            val openApp = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
