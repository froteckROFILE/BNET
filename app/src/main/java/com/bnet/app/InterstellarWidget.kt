package com.bnet.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class InterstellarWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateAppWidget(context, manager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, InterstellarWidget::class.java)
            manager.getAppWidgetIds(component).forEach { updateAppWidget(context, manager, it) }
        }

        private fun updateAppWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val calm = CalmClock.display(System.currentTimeMillis())
            val views = RemoteViews(context.packageName, R.layout.widget_interstellar)
            views.setTextViewText(R.id.widget_time, calm.label)
            views.setTextViewText(R.id.widget_period, "BNET • " + calm.period)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                android.app.PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
