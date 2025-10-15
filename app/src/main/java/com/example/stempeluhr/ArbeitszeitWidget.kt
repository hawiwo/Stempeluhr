package com.example.stempeluhr

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ArbeitszeitWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_arbeitszeit)
            views.setTextViewText(R.id.textViewArbeitszeit, "Lade...")

            val zeit = berechneTageszeit(context)
            views.setTextViewText(R.id.textViewArbeitszeit, "Heute: $zeit")

            // Reload-Intent (auf Widget tippen → aktualisiert)
            val intent = Intent(context, ArbeitszeitWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
            }
            val pending = PendingIntent.getBroadcast(
                context, widgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.textViewArbeitszeit, pending)

            manager.updateAppWidget(widgetId, views)
        }

        private fun berechneTageszeit(context: Context): String {
            val logFile = File(context.filesDir, "stempel.json")
            if (!logFile.exists()) return "0h 00min"

            val gson = Gson()
            val type = object : TypeToken<List<Stempel>>() {}.type
            val stempelListe: List<Stempel> = try {
                gson.fromJson(logFile.readText(), type)
            } catch (e: Exception) {
                return "--"
            }

            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val heute = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val jetzt = System.currentTimeMillis()

            val starts = stempelListe.filter { it.typ == "Start" && it.zeit.startsWith(heute) }
                .mapNotNull { format.parse(it.zeit)?.time }
            val enden = stempelListe.filter { it.typ == "Ende" && it.zeit.startsWith(heute) }
                .mapNotNull { format.parse(it.zeit)?.time }

            var sum = 0L
            val n = minOf(starts.size, enden.size)
            for (i in 0 until n) {
                val diff = enden[i] - starts[i]
                if (diff > 0) sum += diff
            }

            // falls noch eingestempelt
            if (starts.size > enden.size && starts.isNotEmpty()) {
                sum += jetzt - starts.last()
            }

            val minuten = sum / 1000 / 60
            val stunden = minuten / 60
            val rest = minuten % 60
            return "${stunden}h ${rest}min"
        }
    }
}

