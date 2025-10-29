package com.example.stempeluhr

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ArbeitszeitWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            aktualisiereWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun aktualisiereWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.arbeitszeit_widget)

        // Async laden der Datenbankwerte
        GlobalScope.launch(Dispatchers.IO) {
            val statusText = ladeStatusText(context)
            Log.d("WIDGET", "Status geladen: $statusText")

            withContext(Dispatchers.Main) {
                views.setTextViewText(R.id.statusTextView, statusText)

                // Optional: Klick-Intent -> öffnet App
                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.statusTextView, pendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    private suspend fun ladeStatusText(context: Context): String {
        return try {
            val db = StempelDatabase.getDatabase(context)
            val dao = db.stempelDao()
            val liste = dao.getAllOnce()

            if (liste.isEmpty()) return "Keine Daten"

            val letzter = liste.last()
            val eingestempelt = letzter.typ == "Start"

            val zeiten = berechneAlleZeiten(
                liste = liste,
                startZeit = Date(letzter.zeitpunkt),
                eingestempelt = eingestempelt,
                context = context
            )

            val heute = zeiten.heute.ifEmpty { "0h" }
            val status = if (eingestempelt) "🟢 Eingestempelt" else "🔴 Ausgestempelt"

            "Heute: $heute\n$status"
        } catch (e: Exception) {
            Log.e("WIDGET", "Fehler beim Laden: ${e.message}")
            "Fehler beim Laden"
        }
    }
}
