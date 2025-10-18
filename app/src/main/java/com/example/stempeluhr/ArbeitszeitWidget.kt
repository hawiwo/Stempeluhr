package com.example.stempeluhr

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.*

class ArbeitszeitWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.example.stempeluhr.ACTION_REFRESH"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ArbeitszeitWidget::class.java))
            for (id in ids) updateWidget(context, manager, id)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.arbeitszeit_widget)

        val text = ladeStatusText(context)
        views.setTextViewText(R.id.textViewStatus, text)

        val intent = Intent(context, ArbeitszeitWidget::class.java).apply {
            action = ACTION_REFRESH
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Klick auf das ganze Widget = Refresh
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        manager.updateAppWidget(widgetId, views)
    }

    private fun ladeStatusText(context: Context): String {
        val gson = Gson()
        val logFile = File(context.filesDir, "stempel.json")

        if (!logFile.exists()) return "Keine Daten"

        return try {
            val type = object : TypeToken<List<Stempel>>() {}.type
            val liste: List<Stempel> = gson.fromJson(logFile.readText(), type) ?: emptyList()
            if (liste.isEmpty()) return "Keine Daten"

            val letzter = liste.last()
            val eingestempelt = letzter.typ == "Start"
            val startZeit = parseDateFlexible(letzter.zeit)
            val zeiten = berechneAlleZeiten(liste, startZeit, eingestempelt, context)

            val heute = zeiten.heute.ifEmpty { "0h" }
            val status = if (eingestempelt) "🟢 Eingestempelt" else "🔴 Ausgestempelt"

            // Nur noch diese zwei Zeilen
            "Heute: $heute\n$status"
        } catch (e: Exception) {
            "Fehler beim Laden"
        }
    }
}
