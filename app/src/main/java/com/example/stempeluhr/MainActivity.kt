package com.example.stempeluhr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import androidx.core.view.WindowCompat

data class Stempel(val typ: String, val zeit: String)
data class Einstellungen(
    var startwertMinuten: Int = 0,
    var standDatum: String = "" // wann der Wert zuletzt geändert wurde
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // sorgt dafür, dass Systemleisten (Status/Navigationsleiste) korrekt berücksichtigt werden
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent { StempeluhrApp() }
    }
}
@Composable
fun StempeluhrApp() {
    var zeigeEinstellungen by remember { mutableStateOf(false) }

    if (zeigeEinstellungen) {
        EinstellungenScreen(onClose = { zeigeEinstellungen = false })
    } else {
        HauptScreen(onOpenSettings = { zeigeEinstellungen = true })
    }
}

@Composable
fun HauptScreen(onOpenSettings: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gson = remember { Gson() }
    val logFile = File(context.filesDir, "stempel.json")
    val stempelListe = remember { mutableStateListOf<Stempel>() }
    val format = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    var statusText by remember { mutableStateOf("Noch nicht eingestempelt") }
    var istEingestempelt by remember { mutableStateOf(false) }
    var eingestempeltSeit by remember { mutableStateOf<Date?>(null) }

    var arbeitsdauerHeute by remember { mutableStateOf("") }
    var arbeitsdauerWoche by remember { mutableStateOf("") }
    var arbeitsdauerMonat by remember { mutableStateOf("") }
    var arbeitsdauerJahr by remember { mutableStateOf("") }
    var startwertAnzeige by remember { mutableStateOf("") }
    var standDatumAnzeige by remember { mutableStateOf("") }

    // JSON einlesen
    LaunchedEffect(Unit) {
        if (logFile.exists()) {
            try {
                val text = logFile.readText()
                if (text.isNotBlank()) {
                    val type = object : TypeToken<List<Stempel>>() {}.type
                    val geleseneListe: List<Stempel> = Gson().fromJson(text, type) ?: emptyList()
                    stempelListe.addAll(geleseneListe)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (stempelListe.isNotEmpty()) {
            val letzter = stempelListe.last()
            if (letzter.typ == "Start") {
                istEingestempelt = true
                eingestempeltSeit = format.parse(letzter.zeit)
                statusText = "Eingestempelt seit ${letzter.zeit.substring(11)}"
            } else {
                istEingestempelt = false
                statusText = "Ausgestempelt"
            }
        }
    }

    // Live-Aktualisierung
    LaunchedEffect(istEingestempelt, eingestempeltSeit, stempelListe.size) {
        while (true) {
            val zeiten = berechneAlleZeiten(stempelListe, eingestempeltSeit, istEingestempelt, context)
            arbeitsdauerHeute = zeiten.heute
            arbeitsdauerWoche = zeiten.woche
            arbeitsdauerMonat = zeiten.monat
            arbeitsdauerJahr = zeiten.jahr
            startwertAnzeige = zeiten.startwert
            standDatumAnzeige = zeiten.standDatum
            delay(30_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp)
    ) {
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
        Text(statusText, fontSize = 20.sp)
        Spacer(Modifier.height(16.dp))

        if (arbeitsdauerHeute.isNotEmpty()) {
            Text("Heute: $arbeitsdauerHeute", fontSize = 18.sp)
            Text("Diese Woche: $arbeitsdauerWoche", fontSize = 18.sp)
            Text("Diesen Monat: $arbeitsdauerMonat", fontSize = 18.sp)
            Text("Dieses Jahr: $arbeitsdauerJahr", fontSize = 18.sp)
            if (startwertAnzeige.isNotEmpty()) {
                val datumText = if (standDatumAnzeige.isNotEmpty()) " ($standDatumAnzeige)" else ""
                Text(
                    "(inkl. Startwert $startwertAnzeige$datumText)",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            Text("Noch keine Arbeitszeit erfasst", fontSize = 18.sp)
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = {
                val jetzt = Date()
                addStempel("Start", stempelListe, gson, logFile)
                eingestempeltSeit = jetzt
                statusText = "Eingestempelt seit ${format.format(jetzt).substring(11)}"
                istEingestempelt = true
            },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            enabled = !istEingestempelt
        ) { Text("Start", fontSize = 22.sp) }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                addStempel("Ende", stempelListe, gson, logFile)
                statusText = "Ausgestempelt"
                istEingestempelt = false
                eingestempeltSeit = null
            },
            modifier = Modifier.fillMaxWidth().height(80.dp),
            enabled = istEingestempelt
        ) { Text("Ende", fontSize = 22.sp) }
    }
}

fun addStempel(typ: String, liste: MutableList<Stempel>, gson: Gson, file: File) {
    val zeit = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    liste.add(Stempel(typ, zeit))
    file.writeText(gson.toJson(liste))
}

data class ZeitSumme(
    val heute: String,
    val woche: String,
    val monat: String,
    val jahr: String,
    val startwert: String,
    val standDatum: String
)

fun berechneAlleZeiten(
    stempelListe: List<Stempel>,
    startZeit: Date?,
    aktiv: Boolean,
    context: android.content.Context
): ZeitSumme {
    if (stempelListe.isEmpty()) return ZeitSumme("", "", "", "", "", "")

    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val tagFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    val heuteStr = tagFormat.format(Date())
    val jetzt = System.currentTimeMillis()

    var gesamtHeute = 0L
    var gesamtWoche = 0L
    var gesamtMonat = 0L
    var gesamtJahr = 0L

    for ((datum, liste) in stempelListe.groupBy { it.zeit.substring(0, 10) }) {
        val starts = liste.filter { it.typ == "Start" }.mapNotNull { format.parse(it.zeit)?.time }
        val enden = liste.filter { it.typ == "Ende" }.mapNotNull { format.parse(it.zeit)?.time }

        var sum = 0L
        val n = minOf(starts.size, enden.size)
        for (i in 0 until n) {
            val diff = enden[i] - starts[i]
            if (diff > 0) sum += diff
        }

        if (aktiv && datum == heuteStr && startZeit != null) {
            sum += jetzt - startZeit.time
        }

        val d = tagFormat.parse(datum)!!
        cal.time = d
        val jahr = cal.get(Calendar.YEAR)
        val woche = cal.get(Calendar.WEEK_OF_YEAR)
        val monat = cal.get(Calendar.MONTH)

        val calJetzt = Calendar.getInstance()
        if (jahr == calJetzt.get(Calendar.YEAR)) {
            gesamtJahr += sum
            if (monat == calJetzt.get(Calendar.MONTH)) gesamtMonat += sum
            if (woche == calJetzt.get(Calendar.WEEK_OF_YEAR)) gesamtWoche += sum
        }
        if (datum == heuteStr) gesamtHeute += sum
    }

    // gespeicherten Startwert laden
    val settingsFile = File(context.filesDir, "settings.json")
    var startwertMinuten = 0
    var standDatum = ""
    if (settingsFile.exists()) {
        try {
            val einstellungen = Gson().fromJson(settingsFile.readText(), Einstellungen::class.java)
            startwertMinuten = einstellungen.startwertMinuten
            standDatum = einstellungen.standDatum
        } catch (_: Exception) {}
    }

    // Startwert zum Jahreswert hinzufügen
    val gesamtJahrMinuten = gesamtJahr / 1000 / 60 + startwertMinuten
    val gesamtJahrMs = gesamtJahrMinuten * 60 * 1000L

    fun formatZeit(ms: Long): String {
        val minuten = ms / 1000 / 60
        val stunden = floor(minuten / 60.0).toInt()
        val restMin = (minuten % 60).toInt()
        return if (ms != 0L) "${stunden}h ${restMin}min" else ""
    }

    return ZeitSumme(
        heute = formatZeit(gesamtHeute),
        woche = formatZeit(gesamtWoche),
        monat = formatZeit(gesamtMonat),
        jahr = formatZeit(gesamtJahrMs),
        startwert = if (startwertMinuten != 0) formatMinutenAsText(startwertMinuten) else "",
        standDatum = standDatum
    )
}

@Composable
fun EinstellungenScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gson = remember { Gson() }
    val file = File(context.filesDir, "settings.json")
    val logFile = File(context.filesDir, "stempel.json")

    var einstellungen by remember {
        mutableStateOf(
            if (file.exists()) gson.fromJson(file.readText(), Einstellungen::class.java)
            else Einstellungen()
        )
    }

    var startwertText by remember { mutableStateOf(formatMinutenAsText(einstellungen.startwertMinuten)) }
    var standDatum by remember { mutableStateOf(einstellungen.standDatum) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // oberer Bereich
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Einstellungen", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = startwertText,
                onValueChange = { startwertText = it },
                label = { Text("Startwert (Über-/Minusstunden, z. B. +3:45 oder -5:30)") },
                modifier = Modifier.fillMaxWidth()
            )

            if (standDatum.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Stand: $standDatum", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
            }

            Spacer(Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = {
                    val now = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date())
                    einstellungen.startwertMinuten = parseZeitToMinuten(startwertText)
                    einstellungen.standDatum = now
                    file.writeText(gson.toJson(einstellungen))
                    onClose()
                }) { Text("Speichern") }

                Button(onClick = onClose) { Text("Abbrechen") }
            }
        }

        // unterer Bereich (Reset)
        Button(
            onClick = {
                file.delete()
                logFile.delete()
                onClose()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .navigationBarsPadding()
        ) {
            Text("Reset – alle Daten löschen", color = MaterialTheme.colorScheme.onError)
        }
    }
}

// Hilfsfunktionen
fun parseZeitToMinuten(text: String): Int {
    val regex = Regex("(-?)(\\d+):(\\d+)")
    val match = regex.find(text.trim()) ?: return 0
    val neg = match.groupValues[1] == "-"
    val stunden = match.groupValues[2].toInt()
    val minuten = match.groupValues[3].toInt()
    val total = stunden * 60 + minuten
    return if (neg) -total else total
}

fun formatMinutenAsText(minuten: Int): String {
    val neg = minuten < 0
    val absMin = abs(minuten)
    val h = absMin / 60
    val m = absMin % 60
    return (if (neg) "-" else "+") + "%d:%02d".format(h, m)
}
