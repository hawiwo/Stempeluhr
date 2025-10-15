package com.example.stempeluhr

import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TileBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TileService
import androidx.wear.protolayout.RequestBuilders
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ArbeitszeitTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return CallbackToFutureAdapter.getFuture { completer ->
            val arbeitszeit = berechneArbeitszeitHeute(applicationContext)

            // Textanzeige: "Heute: 5h 12min"
            val layout = LayoutElementBuilders.Column.Builder()
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("Stempeluhr")
                        .setFontSize(DimensionBuilders.sp(14f))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Spacer.Builder()
                        .setHeight(DimensionBuilders.dp(4f))
                        .build()
                )
                .addContent(
                    LayoutElementBuilders.Text.Builder()
                        .setText("Heute: $arbeitszeit")
                        .setFontSize(DimensionBuilders.sp(22f))
                        .build()
                )
                .build()

            val tile = TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                .setTimeline(
                    TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(
                            TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(
                                    LayoutElementBuilders.Layout.Builder()
                                        .setRoot(layout)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .build()

            completer.set(tile)
        }
    }

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        return CallbackToFutureAdapter.getFuture { completer ->
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion("1")
                    .build()
            )
        }
    }

    private fun berechneArbeitszeitHeute(context: Context): String {
        val logFile = File(context.filesDir, "stempel.json")
        if (!logFile.exists()) return "0h 00min"

        val gson = Gson()
        val type = object : TypeToken<List<Stempel>>() {}.type
        val stempelListe: List<Stempel> = try {
            gson.fromJson(logFile.readText(), type)
        } catch (_: Exception) {
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
