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

data class Stempel(val typ: String, val zeit: String, val homeoffice: Boolean = false)
data class Einstellungen(
    var startwertMinuten: Int = 0,
    var standDatum: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Systemleisten korrekt berücksichtigen
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
    val logFile = remember { File(context.filesDir, "stempel.json") }
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

    // 1) EINMALIG: JSON laden + Status herstellen (NICHT in der Composition schreiben)
    LaunchedEffect(Unit) {
        try {
            if (logFile.exists()) {
                val text = logFile.readText()
                val type = object : TypeToken<List<Stempel>>() {}.type
                val geleseneListe: List<Stempel> = if (text.isNotBlank()) {
                    try { Gson().fromJson(text, type) ?: emptyList() } catch (_: Exception) { emptyList() }
                } else emptyList()
                stempelListe.clear()
                stempelListe.addAll(geleseneListe)
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
            // Fallback: leere Datei erzeugen
            logFile.writeText("[]")
            stempelListe.clear()
        }
    }

    // 2) PERIODISCH: Zeiten neu berechnen (GENAU EINE Coroutine; bricht automatisch ab)
    LaunchedEffect(istEingestempelt, eingestempeltSeit, stempelListe.size) {
        while (isActive) {
            try {
                val zeiten = berechneAlleZeiten(stempelListe, eingestempeltSeit, istEingestempelt, context)
                arbeitsdauerHeute = zeiten.heute
                arbeitsdauerWoche = zeiten.woche
                arbeitsdauerMonat = zeiten.monat
                arbeitsdauerJahr = zeiten.jahr
                startwertAnzeige = zeiten.startwert
                standDatumAnzeige = zeiten.standDatum
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(60_000) // 1x/Minute reicht (zum Testen kann man 2_000 nehmen)
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
        var homeofficeAktiv by remember { mutableStateOf(false) }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = homeofficeAktiv,
                onCheckedChange = { homeofficeAktiv = it }
            )
            Spacer(Modifier.width(8.dp))
            Text("Homeoffice", fontSize = 18.sp)
        }

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
                if (!istEingestempelt) {
                    val jetzt = Date()
                    addStempel("Start", stempelListe, gson, logFile, homeofficeAktiv)
                    eingestempeltSeit = jetzt
                    statusText = "Eingestempelt seit ${format.format(jetzt).substring(11)}"
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

fun addStempel(
    typ: String,
    liste: MutableList<Stempel>,
    gson: Gson,
    file: File,
    homeoffice: Boolean
) {
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
    val standDatum: String
)

fun parseDateFlexible(s: String): Date? {
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    )
    for (f in formats) {
        try { return f.parse(s) } catch (_: Exception) {}
    }
    return null
}

fun berechneAlleZeiten(
    stempelListe: List<Stempel>,
    startZeit: Date?,
    aktiv: Boolean,
    context: android.content.Context
): ZeitSumme {
    if (stempelListe.isEmpty()) return ZeitSumme("", "", "", "", "", "")

    val tagFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val heuteStr = tagFormat.format(Date())
    val jetzt = System.currentTimeMillis()
    val cal = Calendar.getInstance()

    var gesamtHeute = 0L
    var gesamtWoche = 0L
    var gesamtMonat = 0L
    var gesamtJahr = 0L

    for ((datum, liste) in stempelListe.groupBy { it.zeit.substring(0, 10) }) {
        val starts = liste.filter { it.typ == "Start" }.mapNotNull { parseDateFlexible(it.zeit)?.time }
        val enden = liste.filter { it.typ == "Ende" }.mapNotNull { parseDateFlexible(it.zeit)?.time }

        var sum = 0L
        val n = minOf(starts.size, enden.size)
        for (i in 0 until n) {
            val diff = enden[i] - starts[i]
            if (diff > 0) sum += diff
        }
        // laufende Zeit nur für heute addieren
        if (aktiv && datum == heuteStr && startZeit != null) {
            val diff = jetzt - startZeit.time
            if (diff > 0) sum += diff
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

    // Einstellungen laden
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

    // Jahreswert inkl. Startwert (Minuten nur für Überstundendiff)
    val gesamtJahrMinuten = gesamtJahr / 1000 / 60 + startwertMinuten

    fun formatZeit(ms: Long): String {
        val minuten = ms / 1000 / 60
        val stunden = floor(minuten / 60.0).toInt()
        val restMin = (minuten % 60).toInt()
        return if (ms != 0L) "${stunden}h ${restMin}min" else ""
    }

    // (optional) Überstunden könntest du hier wieder reinrechnen, falls gewünscht

    return ZeitSumme(
        heute = formatZeit(gesamtHeute),
        woche = formatZeit(gesamtWoche),
        monat = formatZeit(gesamtMonat),
        jahr = formatZeit(gesamtJahr), // Wichtig: KEINE zusätzliche Multiplikation/Division
        startwert = if (startwertMinuten != 0) formatMinutenAsText(startwertMinuten) else "",
        standDatum = standDatum
    )
}

@Composable
fun EinstellungenScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val gson = remember { Gson() }
    val settingsFile = remember { File(context.filesDir, "settings.json") }
    val stempelFile = remember { File(context.filesDir, "stempel.json") }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val secondaryColor = MaterialTheme.colorScheme.secondary

    var settingsText by remember {
        mutableStateOf(
            if (settingsFile.exists()) settingsFile.readText()
            else "{\n  \"startwertMinuten\": 0,\n  \"standDatum\": \"\"\n}"
        )
    }
    var stempelText by remember { mutableStateOf(if (stempelFile.exists()) stempelFile.readText() else "[]") }
    var statusText by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(secondaryColor) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Einstellungen & JSON-Dateien", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        Text("settings.json", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = settingsText,
            onValueChange = { settingsText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            singleLine = false
        )

        Spacer(Modifier.height(16.dp))

        Text("stempel.json", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = stempelText,
            onValueChange = { stempelText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            singleLine = false
        )

        Spacer(Modifier.height(16.dp))
        if (statusText.isNotEmpty()) {
            Text(statusText, color = statusColor)
            Spacer(Modifier.height(8.dp))
        }

        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = {
                try {
                    gson.fromJson(settingsText, Einstellungen::class.java)
                    val type = object : TypeToken<List<Stempel>>() {}.type
                    gson.fromJson<List<Stempel>>(stempelText, type)
                    settingsFile.writeText(settingsText)
                    stempelFile.writeText(stempelText)
                    statusText = "Gespeichert ✔"
                    statusColor = primaryColor
                } catch (e: Exception) {
                    statusText = "❌ Ungültiges JSON: ${e.message?.lineSequence()?.firstOrNull() ?: "Syntaxfehler"}"
                    statusColor = errorColor
                }
            }) { Text("Speichern") }

            Button(onClick = onClose) { Text("Schließen") }
        }

        Spacer(Modifier.height(322.dp))
        Button(
            onClick = {
                settingsFile.delete()
                stempelFile.delete()
                statusText = "Beide Dateien gelöscht \u274C"
                statusColor = errorColor
            },
            colors = ButtonDefaults.buttonColors(containerColor = errorColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
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
