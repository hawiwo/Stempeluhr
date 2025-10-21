package com.example.stempeluhr

import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

// ----------------------------------------------------------
// Datenklassen
// ----------------------------------------------------------
data class Stempel(val typ: String, val zeit: String, val homeoffice: Boolean = false)

data class Einstellungen(
    var startwertMinuten: Int = 0,
    var standDatum: String = "",
    var homeofficeAktiv: Boolean = false
)

data class Urlaubseintrag(
    val von: String,
    val bis: String,
    val tage: Int
)

// ----------------------------------------------------------
// Hilfsfunktionen
// ----------------------------------------------------------
fun formatDateYMD(millis: Long?): String {
    if (millis == null) return ""
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return fmt.format(Date(millis))
}

fun formatMinutenAsText(minuten: Int): String {
    val neg = minuten < 0
    val absMin = abs(minuten)
    val h = absMin / 60
    val m = absMin % 60
    return (if (neg) "-" else "+") + "%d:%02d".format(h, m)
}

// ----------------------------------------------------------
// Hauptaktivität
// ----------------------------------------------------------
class MainActivity : ComponentActivity() {
    /* Logcat debug
        override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        // 👇 Testlauf
        testBerechnung(this)

        setContent { StempeluhrApp() }
    }
    */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent { StempeluhrApp() }
    }
}

// ----------------------------------------------------------
// Haupt-App-Struktur
// ----------------------------------------------------------
@Composable
fun StempeluhrApp() {
    var zeigeEinstellungen by remember { mutableStateOf(false) }
    if (zeigeEinstellungen) {
        EinstellungenScreen(onClose = { zeigeEinstellungen = false })
    } else {
        HauptScreen(onOpenSettings = { zeigeEinstellungen = true })
    }
}

// ----------------------------------------------------------
// Hauptansicht (Zeiterfassung)
// ----------------------------------------------------------
@Composable
fun HauptScreen(onOpenSettings: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gson = remember { Gson() }
    val logFile = remember { File(context.filesDir, "stempel.json") }
    val settingsFile = remember { File(context.filesDir, "settings.json") }
    val urlaubFile = remember { File(context.filesDir, "urlaub.json") }
    val stempelListe = remember { mutableStateListOf<Stempel>() }
    val format = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    var statusText by remember { mutableStateOf("Noch nicht eingestempelt") }
    var istEingestempelt by remember { mutableStateOf(false) }
    var eingestempeltSeit by remember { mutableStateOf<Date?>(null) }
    var homeofficeAktiv by remember { mutableStateOf(false) }

    var arbeitsdauerHeute by remember { mutableStateOf("") }
    var arbeitsdauerWoche by remember { mutableStateOf("") }
    var arbeitsdauerMonat by remember { mutableStateOf("") }
    var arbeitsdauerJahr by remember { mutableStateOf("") }
    var ueberstundenText by remember { mutableStateOf("") }
    var startwertAnzeige by remember { mutableStateOf("") }
    var standDatumAnzeige by remember { mutableStateOf("") }

    // Urlaub
    var urlaubGesamt by remember { mutableStateOf(30) }
    var urlaubGenommen by remember { mutableStateOf(0) }
    var urlaubVerbleibend by remember { mutableStateOf(urlaubGesamt) }

    // Homeoffice-Status laden
    LaunchedEffect(Unit) {
        if (settingsFile.exists()) {
            try {
                val jsonText = settingsFile.readText()
                val map = Gson().fromJson<MutableMap<String, Any>>(
                    jsonText,
                    object : TypeToken<MutableMap<String, Any>>() {}.type
                )
                homeofficeAktiv = (map["homeofficeAktiv"] as? Boolean) ?: false
            } catch (_: Exception) {
            }
        }
    }

    fun speichereHomeofficeStatus(aktiv: Boolean) {
        try {
            val jsonText = if (settingsFile.exists()) settingsFile.readText() else "{}"
            val map = Gson().fromJson<MutableMap<String, Any>>(
                jsonText,
                object : TypeToken<MutableMap<String, Any>>() {}.type
            ) ?: mutableMapOf()
            map["homeofficeAktiv"] = aktiv
            settingsFile.writeText(Gson().toJson(map))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // JSON laden
    LaunchedEffect(Unit) {
        try {
            if (logFile.exists()) {
                val text = logFile.readText()
                val type = object : TypeToken<List<Stempel>>() {}.type
                val geleseneListe: List<Stempel> =
                    if (text.isNotBlank()) try {
                        Gson().fromJson(text, type) ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    } else emptyList()
                stempelListe.clear()
                stempelListe.addAll(geleseneListe)
            }

            if (urlaubFile.exists()) {
                try {
                    val json = urlaubFile.readText()
                    val type = object : TypeToken<List<Urlaubseintrag>>() {}.type
                    val liste: List<Urlaubseintrag> = Gson().fromJson(json, type)
                    urlaubGenommen = liste.sumOf { it.tage }
                    urlaubVerbleibend = urlaubGesamt - urlaubGenommen
                } catch (_: Exception) {
                }
            }

            if (stempelListe.isNotEmpty()) {
                val letzter = stempelListe.last()
                if (letzter.typ == "Start") {
                    istEingestempelt = true
                    eingestempeltSeit = parseDateFlexible(letzter.zeit)
                    statusText = "Eingestempelt seit ${letzter.zeit.substring(11)}"
                } else {
                    istEingestempelt = false
                    statusText = "Ausgestempelt"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logFile.writeText("[]")
            stempelListe.clear()
        }
    }

    // Live-Aktualisierung
    LaunchedEffect(istEingestempelt, eingestempeltSeit, stempelListe.size) {
        while (isActive) {
            try {
                val zeiten =
                    berechneAlleZeiten(stempelListe, eingestempeltSeit, istEingestempelt, context)
                arbeitsdauerHeute = zeiten.heute
                arbeitsdauerWoche = zeiten.woche
                arbeitsdauerMonat = zeiten.monat
                arbeitsdauerJahr = zeiten.jahr
                startwertAnzeige = zeiten.startwert
                standDatumAnzeige = zeiten.standDatum
                ueberstundenText = zeiten.ueberstunden
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(60_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stempeluhr", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = homeofficeAktiv,
                    onCheckedChange = {
                        homeofficeAktiv = it
                        speichereHomeofficeStatus(it)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text("Homeoffice", fontSize = 18.sp)
            }

            Spacer(Modifier.height(12.dp))
            Text(statusText, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))

            if (arbeitsdauerHeute.isNotEmpty()) {
                Text("Heute: $arbeitsdauerHeute", fontSize = 18.sp)
                Text("Diese Woche: $arbeitsdauerWoche", fontSize = 18.sp)
                Text("Diesen Monat: $arbeitsdauerMonat", fontSize = 18.sp)
                Text("Dieses Jahr: $arbeitsdauerJahr", fontSize = 18.sp)

// Fortschrittsanzeige für den Tag
                val regex = Regex("(\\d+)h\\s*(\\d+)?min?")
                val match = regex.find(arbeitsdauerHeute)
                val stunden = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val minuten = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val gesamtMinuten = stunden * 60 + minuten
                val fortschritt = (gesamtMinuten / 480f).coerceIn(0f, 1f) // 480 Minuten = 8 Stunden

// animierter Fortschritt
                val animatedProgress = animateFloatAsState(
                    targetValue = fortschritt,
                    animationSpec = tween(durationMillis = 800)
                ).value

// dynamische Farbe je nach Fortschritt
                val progressColor = when {
                    fortschritt >= 1f -> MaterialTheme.colorScheme.primary   // ≥ 8h → grün/blau
                    fortschritt >= 0.5f -> MaterialTheme.colorScheme.tertiary // 4–8h → gelb
                    else -> MaterialTheme.colorScheme.error                   // <4h → rot
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = animatedProgress,
                        strokeWidth = 12.dp,
                        color = progressColor,
                        modifier = Modifier.size(120.dp)
                    )
                    Text(
                        text = String.format("%.0f%%", fortschritt * 100),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Heute gearbeitet: ${arbeitsdauerHeute} von 8h",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(12.dp))

                if (ueberstundenText.isNotEmpty()) {
                    val color = if (ueberstundenText.startsWith("-"))
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                    val label =
                        if (ueberstundenText.startsWith("-")) "Minusstunden" else "Überstunden"
                    Text(
                        "$label: $ueberstundenText",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }

                if (startwertAnzeige.isNotEmpty()) {
                    val datumText =
                        if (standDatumAnzeige.isNotEmpty()) " ($standDatumAnzeige)" else ""
                    Text(
                        "(inkl. Startwert $startwertAnzeige$datumText)",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Urlaub: $urlaubGenommen / $urlaubGesamt Tage genommen", fontSize = 18.sp)
                Text(
                    "Verbleibend: $urlaubVerbleibend Tage",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Column {
            Button(
                onClick = {
                    if (!istEingestempelt) {
                        val jetzt = Date()
                        addStempel("Start", stempelListe, gson, logFile, homeofficeAktiv)
                        eingestempeltSeit = jetzt
                        statusText =
                            "Eingestempelt seit ${format.format(jetzt).substring(11)}"
                        istEingestempelt = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                enabled = !istEingestempelt
            ) { Text("Start", fontSize = 22.sp) }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (istEingestempelt) {
                        addStempel("Ende", stempelListe, gson, logFile, homeofficeAktiv)
                        statusText = "Ausgestempelt"
                        istEingestempelt = false
                        eingestempeltSeit = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                enabled = istEingestempelt
            ) { Text("Ende", fontSize = 22.sp) }
        }
    }
}

// ----------------------------------------------------------
// Berechnung / Stempel
// ----------------------------------------------------------
fun addStempel(typ: String, liste: MutableList<Stempel>, gson: Gson, file: File, homeoffice: Boolean) {
    val zeit = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    liste.add(Stempel(typ, zeit, homeoffice))
    file.writeText(gson.toJson(liste))
}

data class ZeitSumme(
    val heute: String,
    val woche: String,
    val monat: String,
    val jahr: String,
    val startwert: String,
    val standDatum: String,
    val ueberstunden: String
)

fun parseDateFlexible(s: String): Date? {
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    )
    for (f in formats) try {
        return f.parse(s)
    } catch (_: Exception) {
    }
    return null
}
//----------------------------------------------------------------------------------------------------
/**
 * Berechnet alle relevanten Zeiträume basierend auf der Stempelliste
 * Mit verbesserter Unterstützung für Datumsüberschreitungen/Nachtschichten
 */
fun berechneAlleZeiten(
    stempelListe: List<Stempel>,
    startZeit: Date?,
    aktiv: Boolean,
    context: Context
): ZeitSumme {
    if (stempelListe.isEmpty())
        return ZeitSumme("", "", "", "", "", "", "+0:00")

    val tagFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val jetzt = System.currentTimeMillis()
    val heuteStr = tagFormat.format(Date(jetzt))
    val jetztDate = Date(jetzt)

    // --- Einstellungen laden
    val (startwertMinuten, standDatum) = ladeEinstellungen(context, tagFormat)

    // --- Stichtag am Wochenende auf Montag verschieben
    val korrigierterStichtag = korrigiereWochenenden(standDatum)

    // --- Stempel sortieren und zu Paaren kombinieren
    val zeitpaare = erzeugeZeitpaare(stempelListe, startZeit, aktiv, jetztDate)

    // --- Zeiten pro Tag berechnen (mit Berücksichtigung von Datumsübergängen)
    val tageszuordnung = berechneTageszuordnung(zeitpaare, tagFormat)

    // --- Zeitberechnungen für verschiedene Zeiträume
    val zeiten = berechneZeitraeume(tageszuordnung, tagFormat, korrigierterStichtag)

    // --- Sollzeit und Überstunden berechnen
    val (gesamtSeitStichtag, ueberstundenText) = berechneSollzeitUndUeberstunden(
        zeiten.seitStichtag,
        korrigierterStichtag,
        startwertMinuten
    )

    // Ergebnis-Objekt erstellen
    return ZeitSumme(
        heute = formatZeit(zeiten.heute),
        woche = formatZeit(zeiten.woche),
        monat = formatZeit(zeiten.monat),
        jahr = formatZeit(zeiten.jahr),
        startwert = if (startwertMinuten != 0) formatMinutenAsText(startwertMinuten) else "",
        standDatum = korrigierterStichtag?.let { tagFormat.format(it) } ?: "",
        ueberstunden = ueberstundenText
    )
}

/**
 * Lädt Einstellungen aus der settings.json Datei
 */
private fun ladeEinstellungen(context: Context, tagFormat: SimpleDateFormat): Pair<Int, Date?> {
    val settingsFile = File(context.filesDir, "settings.json")
    var startwertMinuten = 0
    var standDatum: Date? = null

    if (settingsFile.exists()) {
        try {
            val einstellungen = Gson().fromJson(settingsFile.readText(), Einstellungen::class.java)
            startwertMinuten = einstellungen.startwertMinuten
            if (einstellungen.standDatum.isNotBlank()) {
                standDatum = tagFormat.parse(einstellungen.standDatum)
            }
        } catch (e: Exception) {
            //Log.e("ZeitErfassung", "Fehler beim Laden der Einstellungen", e)
        }
    }

    return Pair(startwertMinuten, standDatum)
}

/**
 * Wenn der Stichtag auf ein Wochenende fällt, auf Montag verschieben
 */
private fun korrigiereWochenenden(standDatum: Date?): Date? {
    if (standDatum == null) return null

    val calTmp = Calendar.getInstance().apply { time = standDatum }
    when (calTmp.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY -> calTmp.add(Calendar.DAY_OF_YEAR, 2)
        Calendar.SUNDAY -> calTmp.add(Calendar.DAY_OF_YEAR, 1)
    }
    return calTmp.time
}

/**
 * Erzeugt zeitlich geordnete Start-Ende-Paare aus der Stempelliste
 */
private fun erzeugeZeitpaare(
    stempelListe: List<Stempel>,
    startZeit: Date?,
    aktiv: Boolean,
    jetztDate: Date
): List<Pair<Date, Date>> {
    // Stempel sortieren
    val sortierteStempel = stempelListe.mapNotNull { stempel ->
        val zeit = parseDateFlexible(stempel.zeit)
        if (zeit != null) Pair(zeit, stempel.typ) else null
    }.sortedBy { it.first.time }

    // Start/Ende-Paare bilden
    val zeitpaare = mutableListOf<Pair<Date, Date>>()
    var aktuellerStart: Date? = null

    for ((zeitpunkt, typ) in sortierteStempel) {
        when (typ) {
            "Start" -> {
                // Falls ein vorheriger Start existiert, ignorieren oder optional loggen
                aktuellerStart = zeitpunkt
            }
            "Ende" -> {
                if (aktuellerStart != null) {
                    if (aktuellerStart.time < zeitpunkt.time) {
                        zeitpaare.add(Pair(aktuellerStart!!, zeitpunkt))
                    }
                    aktuellerStart = null
                }
                // Ende ohne Start ignorieren oder optional loggen
            }
        }
    }

    // Aktuell laufende Zeit hinzufügen, wenn aktiv
    if (aktiv && aktuellerStart != null && startZeit != null) {
        zeitpaare.add(Pair(startZeit, jetztDate))
    }

    return zeitpaare
}

/**
 * Berechnet Zeiträume nach Tagen, auch bei Datumsüberschreitungen
 */
private fun berechneTageszuordnung(
    zeitpaare: List<Pair<Date, Date>>,
    tagFormat: SimpleDateFormat
): Map<String, Long> {
    val tageszuordnung = mutableMapOf<String, Long>()

    for ((start, ende) in zeitpaare) {
        val startDatum = tagFormat.format(start)
        val endeDatum = tagFormat.format(ende)

        // Fall 1: Starten und Enden am selben Tag
        if (startDatum == endeDatum) {
            val zeitDauer = ende.time - start.time
            tageszuordnung[startDatum] = (tageszuordnung[startDatum] ?: 0L) + zeitDauer
        }
        // Fall 2: Datumsüberschreitung
        else {
            // Hilfskalender für Mitternachtsberechnung
            val mitternachtCal = Calendar.getInstance().apply {
                time = start
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // Zeit vom Start bis Mitternacht
            val startBisMitternacht = mitternachtCal.timeInMillis - start.time
            tageszuordnung[startDatum] = (tageszuordnung[startDatum] ?: 0L) + startBisMitternacht

            // Für mehrtägige Zeiträume: alle vollständigen Tage dazwischen
            var currentCal = Calendar.getInstance().apply {
                timeInMillis = mitternachtCal.timeInMillis
            }

            val endeCal = Calendar.getInstance().apply { time = ende }
            endeCal.set(Calendar.HOUR_OF_DAY, 0)
            endeCal.set(Calendar.MINUTE, 0)
            endeCal.set(Calendar.SECOND, 0)
            endeCal.set(Calendar.MILLISECOND, 0)

            // Für jeden vollständigen Tag dazwischen (bei mehrtägigen Zeiträumen)
            while (currentCal.timeInMillis < endeCal.timeInMillis) {
                val currentDate = currentCal.time
                val currentDateStr = tagFormat.format(currentDate)

                // 24 Stunden in Millisekunden
                tageszuordnung[currentDateStr] = (tageszuordnung[currentDateStr] ?: 0L) + (24 * 60 * 60 * 1000)

                currentCal.add(Calendar.DAY_OF_YEAR, 1)
            }

            // Zeit von Mitternacht bis Ende am letzten Tag
            val mitternachtBisEnde = ende.time - endeCal.timeInMillis
            tageszuordnung[endeDatum] = (tageszuordnung[endeDatum] ?: 0L) + mitternachtBisEnde
        }
    }

    return tageszuordnung
}

/**
 * Berechnet die verschiedenen Zeiträume (heute, Woche, Monat, Jahr, ab Stichtag)
 */
private fun berechneZeitraeume(
    tageszuordnung: Map<String, Long>,
    tagFormat: SimpleDateFormat,
    standDatum: Date?
): ZeitraumSummen {
    var gesamtHeute = 0L
    var gesamtWoche = 0L
    var gesamtMonat = 0L
    var gesamtJahr = 0L
    var gesamtSeitStichtag = 0L

    // Aktuelle Kalender-Werte für Vergleiche
    val calJetzt = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
    }
    val heuteStr = tagFormat.format(calJetzt.time)
    val aktuellesJahr = calJetzt.get(Calendar.YEAR)
    val aktuellerMonat = calJetzt.get(Calendar.MONTH)
    val aktuelleWoche = calJetzt.get(Calendar.WEEK_OF_YEAR)

    // Tageszeiten zu entsprechenden Summen addieren
    for ((datumStr, zeitDauer) in tageszuordnung) {
        val datumDate = tagFormat.parse(datumStr) ?: continue

        // Prüfen auf heute/Woche/Monat/Jahr für Summierung
        val cal = Calendar.getInstance().apply {
            time = datumDate
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }

        val jahr = cal.get(Calendar.YEAR)
        val monat = cal.get(Calendar.MONTH)
        val woche = cal.get(Calendar.WEEK_OF_YEAR)

        // Entsprechenden Zeiträumen zuordnen
        if (datumStr == heuteStr) {
            gesamtHeute += zeitDauer
        }

        if (jahr == aktuellesJahr) {
            gesamtJahr += zeitDauer

            if (monat == aktuellerMonat) {
                gesamtMonat += zeitDauer
            }

            if (woche == aktuelleWoche) {
                gesamtWoche += zeitDauer
            }
        }

        // Ab Stichtag zählen
        if (standDatum == null || !datumDate.before(standDatum)) {
            gesamtSeitStichtag += zeitDauer
        }
    }

    return ZeitraumSummen(
        heute = gesamtHeute,
        woche = gesamtWoche,
        monat = gesamtMonat,
        jahr = gesamtJahr,
        seitStichtag = gesamtSeitStichtag
    )
}

/**
 * Berechnet Sollzeit und Überstunden
 */
private fun berechneSollzeitUndUeberstunden(
    gesamtSeitStichtag: Long,
    standDatum: Date?,
    startwertMinuten: Int
): Pair<Long, String> {
    // Sollzeit berechnen (nur Arbeitstage Mo–Fr)
    var sollzeitMinuten = 0
    val calStart = Calendar.getInstance()

    if (standDatum != null) {
        calStart.time = standDatum
    } else {
        calStart.apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val calEnd = Calendar.getInstance()

    while (calStart.before(calEnd) || isSameDay(calStart, calEnd)) {
        val tag = calStart.get(Calendar.DAY_OF_WEEK)
        if (tag in Calendar.MONDAY..Calendar.FRIDAY) {
            sollzeitMinuten += 8 * 60  // 8 Stunden pro Arbeitstag
        }
        calStart.add(Calendar.DAY_OF_YEAR, 1)
    }

    // Überstundenberechnung
    val istMinuten = (gesamtSeitStichtag / 1000 / 60).toInt()
    val diffMinuten = istMinuten + startwertMinuten - sollzeitMinuten

    return Pair(gesamtSeitStichtag, formatMinutenAsText(diffMinuten))
}

/**
 * Prüft, ob zwei Calendar-Objekte den gleichen Tag darstellen
 */
private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

/**
 * Formatiert Millisekunden in einen lesbaren Zeitstring
 */
private fun formatZeit(ms: Long): String {
    if (ms == 0L) return ""

    val minuten = ms / 1000 / 60
    val stunden = Math.floor(minuten / 60.0).toInt()
    val restMin = (minuten % 60).toInt()

    return "${stunden}h ${restMin}min"
}

/**
 * Hilfsklasse für die Zeitraum-Summen
 */
private data class ZeitraumSummen(
    val heute: Long,
    val woche: Long,
    val monat: Long,
    val jahr: Long,
    val seitStichtag: Long
)

// ----------------------------------------------------------
// Einstellungsseite mit Material3-Kalendern
// ----------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinstellungenScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gson = remember { Gson() }
    val settingsFile = remember { File(context.filesDir, "settings.json") }
    val stempelFile = remember { File(context.filesDir, "stempel.json") }
    val urlaubFile = remember { File(context.filesDir, "urlaub.json") }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val secondaryColor = MaterialTheme.colorScheme.secondary

    var settingsText by remember {
        mutableStateOf(
            if (settingsFile.exists()) settingsFile.readText()
            else "{\n  \"startwertMinuten\": 0,\n  \"standDatum\": \"\",\n  \"homeofficeAktiv\": false\n}"
        )
    }
    var stempelText by remember { mutableStateOf(if (stempelFile.exists()) stempelFile.readText() else "[]") }
    var urlaubText by remember { mutableStateOf(if (urlaubFile.exists()) urlaubFile.readText() else "[]") }

    var statusText by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(secondaryColor) }

    val scrollState = rememberScrollState()

    // bestehende Urlaubsliste laden
    val urlaubsliste = remember {
        mutableStateListOf<Urlaubseintrag>().apply {
            if (urlaubFile.exists()) {
                try {
                    val type = object : TypeToken<List<Urlaubseintrag>>() {}.type
                    val daten: List<Urlaubseintrag> =
                        Gson().fromJson(urlaubFile.readText(), type) ?: emptyList()
                    addAll(daten.sortedBy { it.von })
                } catch (_: Exception) {
                }
            }
        }
    }

    // -------- Layout --------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Einstellungen & JSON-Dateien", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Text("settings.json", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = settingsText,
            onValueChange = { settingsText = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            singleLine = false
        )

        Spacer(Modifier.height(16.dp))
        Text("stempel.json", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = stempelText,
            onValueChange = { stempelText = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            singleLine = false
        )

        Spacer(Modifier.height(16.dp))
        Text("urlaub.json", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = urlaubText,
            onValueChange = { urlaubText = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            singleLine = false
        )

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Text("Neuen Urlaub hinzufügen", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        // Kalenderauswahl
        var vonDatum by remember { mutableStateOf("") }
        var bisDatum by remember { mutableStateOf("") }
        var tageBerechnet by remember { mutableStateOf(0) }

        var showVonPicker by remember { mutableStateOf(false) }
        var showBisPicker by remember { mutableStateOf(false) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showVonPicker = true }, modifier = Modifier.weight(1f)) {
                Text(if (vonDatum.isEmpty()) "Von: Datum wählen" else "Von: $vonDatum")
            }
            OutlinedButton(onClick = { showBisPicker = true }, modifier = Modifier.weight(1f)) {
                Text(if (bisDatum.isEmpty()) "Bis: Datum wählen" else "Bis: $bisDatum")
            }
        }

        if (showVonPicker) {
            val state = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showVonPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        vonDatum = formatDateYMD(state.selectedDateMillis)
                        showVonPicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showVonPicker = false }) { Text("Abbrechen") } }
            ) { DatePicker(state = state) }
        }

        if (showBisPicker) {
            val state = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showBisPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        bisDatum = formatDateYMD(state.selectedDateMillis)
                        showBisPicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showBisPicker = false }) { Text("Abbrechen") } }
            ) { DatePicker(state = state) }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                try {
                    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val start = format.parse(vonDatum)
                    val ende = format.parse(bisDatum)
                    if (start != null && ende != null && !ende.before(start)) {
                        var tage = 0
                        val cal = Calendar.getInstance()
                        cal.time = start
                        while (!cal.time.after(ende)) {
                            val tag = cal.get(Calendar.DAY_OF_WEEK)
                            if (tag in Calendar.MONDAY..Calendar.FRIDAY) tage++
                            cal.add(Calendar.DAY_OF_YEAR, 1)
                        }
                        tageBerechnet = tage

                        val neuerUrlaub = Urlaubseintrag(vonDatum, bisDatum, tage)
                        urlaubsliste.add(neuerUrlaub)
                        val neuesJson = Gson().toJson(urlaubsliste)
                        urlaubText = neuesJson
                        urlaubFile.writeText(neuesJson)

                        statusText = "Urlaub hinzugefügt: $tage Tage ✔"
                        statusColor = primaryColor
                    } else {
                        statusText = "❌ Ungültiges Datum"
                        statusColor = errorColor
                    }
                } catch (e: Exception) {
                    statusText = "❌ Fehler: ${e.message}"
                    statusColor = errorColor
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Hinzufügen") }

        if (tageBerechnet > 0) {
            Text("Berechnet: $tageBerechnet Tage (Mo–Fr)", color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        // Urlaubsliste anzeigen
        Text("Gespeicherte Urlaube", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (urlaubsliste.isEmpty()) {
            Text("Keine Einträge", color = MaterialTheme.colorScheme.secondary)
        } else {
            urlaubsliste.sortedBy { it.von }.forEach {
                Text(
                    "📅 ${it.von} – ${it.bis} · ${it.tage} Tage",
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        // JSON speichern / schließen
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = {
                try {
                    gson.fromJson(settingsText, Einstellungen::class.java)
                    val typeStempel = object : TypeToken<List<Stempel>>() {}.type
                    gson.fromJson<List<Stempel>>(stempelText, typeStempel)
                    val typeUrlaub = object : TypeToken<List<Urlaubseintrag>>() {}.type
                    gson.fromJson<List<Urlaubseintrag>>(urlaubText, typeUrlaub)

                    settingsFile.writeText(settingsText)
                    stempelFile.writeText(stempelText)
                    urlaubFile.writeText(urlaubText)

                    statusText = "Gespeichert ✔"
                    statusColor = primaryColor
                } catch (e: Exception) {
                    statusText =
                        "❌ Ungültiges JSON: ${e.message?.lineSequence()?.firstOrNull() ?: "Syntaxfehler"}"
                    statusColor = errorColor
                }
            }) { Text("Speichern") }

            Button(onClick = onClose) { Text("Schließen") }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val meldung = backupDateien(context.applicationContext)
                statusText = meldung
                statusColor = primaryColor
            },
            modifier = Modifier.fillMaxWidth().height(60.dp)
        ) { Text("Backup erstellen") }

        Spacer(Modifier.height(12.dp))
        if (statusText.isNotEmpty()) Text(statusText, color = statusColor)
    }
}


// ----------------------------------------------------------
// Backup
// ----------------------------------------------------------
fun backupDateien(context: Context): String {
    val quelleDir = context.filesDir
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val zielDir = File(downloads, "StempeluhrBackup")
    if (!zielDir.exists()) zielDir.mkdirs()

    val zeitstempel = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    val stempelFile = File(quelleDir, "stempel.json")
    val settingsFile = File(quelleDir, "settings.json")
    val urlaubFile = File(quelleDir, "urlaub.json")

    return try {
        if (stempelFile.exists()) stempelFile.copyTo(File(zielDir, "stempel_$zeitstempel.json"), overwrite = true)
        if (settingsFile.exists()) settingsFile.copyTo(File(zielDir, "settings_$zeitstempel.json"), overwrite = true)
        if (urlaubFile.exists()) urlaubFile.copyTo(File(zielDir, "urlaub_$zeitstempel.json"), overwrite = true)
        "Backup gespeichert unter: ${zielDir.path}"
    } catch (e: Exception) {
        "Fehler beim Backup: ${e.message}"
    }
}
fun testBerechnung(context: Context) {
    println("=== TEST STARTE ===")

    val gson = Gson()
    val stempelFile = File(context.filesDir, "stempel.json")
    val settingsFile = File(context.filesDir, "settings.json")

    if (!stempelFile.exists() || !settingsFile.exists()) {
        println("Fehler: Dateien fehlen")
        return
    }

    val stempelListe: List<Stempel> =
        gson.fromJson(stempelFile.readText(), object : TypeToken<List<Stempel>>() {}.type)
    val einstellungen = gson.fromJson(settingsFile.readText(), Einstellungen::class.java)

    val jetzt = Date()
    val result = berechneAlleZeiten(stempelListe, null, false, context)

    println("=== DEBUG AUSGABE ===")
    println("Stichtag: ${einstellungen.standDatum}")
    println("Startwert (Minuten): ${einstellungen.startwertMinuten}")
    println("Heute: ${result.heute}")
    println("Woche: ${result.woche}")
    println("Monat: ${result.monat}")
    println("Jahr: ${result.jahr}")
    println("Überstunden: ${result.ueberstunden}")
    println("Berechnet am: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(jetzt)}")
    println("=====================")
}
