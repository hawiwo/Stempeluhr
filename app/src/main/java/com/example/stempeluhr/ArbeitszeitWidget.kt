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

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val gson = Gson()
        val logFile = File(context.filesDir, "stempel.json")
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        var text = "Noch keine Daten"
        var homeofficeHinweis = ""

        if (logFile.exists()) {
            try {
                val type = object : TypeToken<List<Stempel>>() {}.type
                val stempelListe: List<Stempel> = gson.fromJson(logFile.readText(), type) ?: emptyList()
                if (stempelListe.isNotEmpty()) {
                    val letzter = stempelListe.last()
                    val zeiten = berechneAlleZeiten(stempelListe, null, false, context)
                    text = "Heute: ${zeiten.heute.ifEmpty { "–" }}"
                    if (letzter.homeoffice) {
                        homeofficeHinweis = "Du bist im Homeoffice"
                    }
                }
            } catch (e: Exception) {
                text = "Fehler beim Lesen"
            }
        }

        val views = RemoteViews(context.packageName, R.layout.arbeitszeit_widget)
        views.setTextViewText(R.id.textViewArbeitszeit, text)
        views.setTextViewText(R.id.textViewHomeoffice, homeofficeHinweis)

        // Tap → öffnet App
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
