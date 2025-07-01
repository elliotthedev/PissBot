package com.landofthefallen.pissbot

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.landofthefallen.pissbot.R

class TestWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Create the RemoteViews object
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Create PendingIntent for the button
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.landofthefallen.pissbot.SEND_TEST"
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Set up the button click
        views.setOnClickPendingIntent(R.id.widget_test_button, pendingIntent)

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
} 