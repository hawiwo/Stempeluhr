package com.example.stempeluhr

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun parseDateFlexible(s: String): Date? {
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    )
    for (f in formats) try {
        return f.parse(s)
    } catch (_: Exception) {}
    return null
}

fun formatZeit(ms: Long): String {
    if (ms == 0L) return ""
    val minuten = ms / 1000 / 60
    val stunden = floor(minuten / 60.0).toInt()
    val restMin = (minuten % 60).toInt()
    return "${stunden}h ${restMin}min"
}

data class ZeitenErgebnis(
    val heute: String,
    val gesamt: String
)

fun berechneAlleZeiten(
    liste: List<StempelEintrag>,
    startZeit: Date,
    eingestempelt: Boolean,
    context: Context
): ZeitenErgebnis {
    if (liste.isEmpty()) return ZeitenErgebnis("0h", "0h")

    var gesamtMinuten = 0
    var heuteMinuten = 0

    val heuteDatum = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val formatTag = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    for (i in 0 until liste.size - 1) {
        val start = Date(liste[i].zeitpunkt)
        val ende = Date(liste[i + 1].zeitpunkt)
        if (liste[i].typ == "Start" && liste[i + 1].typ == "Ende") {
            val diffMin = ((ende.time - start.time) / 60000).toInt()
            gesamtMinuten += diffMin
            if (formatTag.format(start) == heuteDatum) {
                heuteMinuten += diffMin
            }
        }
    }

    val heuteH = heuteMinuten / 60
    val heuteM = heuteMinuten % 60
    val gesamtH = gesamtMinuten / 60
    val gesamtM = gesamtMinuten % 60
    Log.d("ZEIT", "Heute=$heuteMinuten min, Gesamt=$gesamtMinuten min")

    return ZeitenErgebnis(
        heute = String.format("%dh %02dm", heuteH, heuteM),
        gesamt = String.format("%dh %02dm", gesamtH, gesamtM)
    )
}

private fun ladeEinstellungen(context: Context, tagFormat: SimpleDateFormat): Pair<Int, Date?> {
    val settingsRepo = SettingsRepository(context)
    var startwert = 0
    var standDatumStr = ""
    var standDatum: Date? = null

    runBlocking {
        startwert = settingsRepo.startwertFlow.first()
        standDatumStr = settingsRepo.standDatumFlow.first()
    }

    if (standDatumStr.isNotEmpty()) {
        try {
            standDatum = tagFormat.parse(standDatumStr)
        } catch (_: Exception) {
            standDatum = null
        }
    }

    return Pair(startwert, standDatum)
}
private fun korrigiereWochenenden(standDatum: Date?): Date? {
    if (standDatum == null) return null
    val cal = Calendar.getInstance().apply { time = standDatum }
    when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> cal.add(Calendar.DAY_OF_YEAR, 2)
        Calendar.SUNDAY -> cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.time
}

private fun erzeugeZeitpaare(
    stempelListe: List<Stempel>,
    startZeit: Date?,
    aktiv: Boolean,
    jetztDate: Date
): List<Pair<Date, Date>> {
    val sortierteStempel = stempelListe.mapNotNull {
        val zeit = parseDateFlexible(it.zeit)
        if (zeit != null) Pair(zeit, it.typ) else null
    }.sortedBy { it.first.time }

    val zeitpaare = mutableListOf<Pair<Date, Date>>()
    var aktuellerStart: Date? = null

    for ((zeitpunkt, typ) in sortierteStempel) {
        when (typ) {
            "Start" -> aktuellerStart = zeitpunkt
            "Ende" -> if (aktuellerStart != null && aktuellerStart.time < zeitpunkt.time) {
                zeitpaare.add(Pair(aktuellerStart!!, zeitpunkt))
                aktuellerStart = null
            }
        }
    }

    if (aktiv && aktuellerStart != null && startZeit != null) {
        zeitpaare.add(Pair(startZeit, jetztDate))
    }

    return zeitpaare
}

private fun berechneTageszuordnung(
    zeitpaare: List<Pair<Date, Date>>,
    tagFormat: SimpleDateFormat
): Map<String, Long> {
    val map = mutableMapOf<String, Long>()

    for ((start, ende) in zeitpaare) {
        val startDatum = tagFormat.format(start)
        val endeDatum = tagFormat.format(ende)

        if (startDatum == endeDatum) {
            map[startDatum] = (map[startDatum] ?: 0L) + (ende.time - start.time)
        } else {
            val mitternacht = Calendar.getInstance().apply {
                time = start
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            map[startDatum] = (map[startDatum] ?: 0L) + (mitternacht - start.time)
            map[endeDatum] = (map[endeDatum] ?: 0L) + (ende.time - mitternacht)
        }
    }

    return map
}

private data class ZeitraumSummen(
    val heute: Long,
    val woche: Long,
    val monat: Long,
    val jahr: Long,
    val seitStichtag: Long
)

private fun berechneZeitraeume(
    tageszuordnung: Map<String, Long>,
    tagFormat: SimpleDateFormat,
    standDatum: Date?
): ZeitraumSummen {
    var heute = 0L
    var woche = 0L
    var monat = 0L
    var jahr = 0L
    var seitStichtag = 0L

    val calJetzt = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }

    val heuteStr = tagFormat.format(calJetzt.time)
    val jahrJetzt = calJetzt.get(Calendar.YEAR)
    val monatJetzt = calJetzt.get(Calendar.MONTH)
    val wocheJetzt = calJetzt.get(Calendar.WEEK_OF_YEAR)

    for ((datumStr, zeit) in tageszuordnung) {
        val d = tagFormat.parse(datumStr) ?: continue
        val cal = Calendar.getInstance().apply {
            time = d
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }

        if (datumStr == heuteStr) heute += zeit
        if (cal.get(Calendar.YEAR) == jahrJetzt) {
            jahr += zeit
            if (cal.get(Calendar.MONTH) == monatJetzt) monat += zeit
            if (cal.get(Calendar.WEEK_OF_YEAR) == wocheJetzt) woche += zeit
        }
        if (standDatum == null || !d.before(standDatum)) seitStichtag += zeit
    }

    return ZeitraumSummen(heute, woche, monat, jahr, seitStichtag)
}

private fun berechneSollzeitUndUeberstunden(
    gesamtSeitStichtag: Long,
    standDatum: Date?,
    startwertMinuten: Int
): Pair<Long, String> {
    var sollzeitMinuten = 0
    val calStart = Calendar.getInstance()
    if (standDatum != null) calStart.time = standDatum
    else {
        calStart.set(Calendar.MONTH, Calendar.JANUARY)
        calStart.set(Calendar.DAY_OF_MONTH, 1)
    }

    val calEnd = Calendar.getInstance()
    while (calStart.before(calEnd) || isSameDay(calStart, calEnd)) {
        val tag = calStart.get(Calendar.DAY_OF_WEEK)
        if (tag in Calendar.MONDAY..Calendar.FRIDAY) sollzeitMinuten += 8 * 60
        calStart.add(Calendar.DAY_OF_YEAR, 1)
    }

    val istMinuten = (gesamtSeitStichtag / 1000 / 60).toInt()
    val diff = istMinuten + startwertMinuten - sollzeitMinuten
    return Pair(gesamtSeitStichtag, formatMinutenAsText(diff))
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
fun addStempel(typ: String, liste: MutableList<Stempel>, gson: Gson, file: File, homeoffice: Boolean) {
    val zeit = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    liste.add(Stempel(typ, zeit, homeoffice))
    file.writeText(gson.toJson(liste))
}
