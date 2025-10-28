package com.example.stempeluhr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.*
import androidx.activity.viewModels
import com.example.feiertage.holeFeiertageBW

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
// Hauptaktivität
// ----------------------------------------------------------
class MainActivity : ComponentActivity() {
    private val viewModel: StempelViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContent { StempeluhrApp() }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshCalculationsOnce()
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
fun AbschnittTitel(text: String) {
    Text(
        text,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun HauptScreen(
    viewModel: StempelViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onOpenSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            HeaderSection(
                homeoffice = state.homeofficeAktiv,
                onHomeofficeChange = viewModel::toggleHomeoffice,
                onOpenSettings = onOpenSettings
            )

            Spacer(Modifier.height(12.dp))
            BodySection(state)
        }

        Button(
            onClick = { viewModel.toggleStempel() },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.istEingestempelt)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (state.istEingestempelt) "Ende" else "Start", fontSize = 22.sp)
        }
    }
}
@Composable
fun HeaderSection(
    homeoffice: Boolean,
    onHomeofficeChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Stempeluhr", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Checkbox(
                    checked = homeoffice,
                    onCheckedChange = onHomeofficeChange,
                    modifier = Modifier.size(24.dp).offset(y = (-2).dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Homeoffice", fontSize = 18.sp)
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
        }
    }
}
@Composable
fun BodySection(state: ZeiterfassungState) {
    Text(state.statusText, fontSize = 20.sp)
    Spacer(Modifier.height(6.dp))

    if (state.arbeitsdauerHeute.isNotEmpty()) {
        ArbeitszeitFortschritt(heute = state.arbeitsdauerHeute, woche = state.arbeitsdauerWoche)
        Spacer(Modifier.height(12.dp))
        Text("Diesen Monat: ${state.arbeitsdauerMonat}", fontSize = 18.sp)
        Text("Dieses Jahr: ${state.arbeitsdauerJahr}", fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        WochenUebersicht(
            stempelListe = state.stempelListe,
            istEingestempelt = state.istEingestempelt,
            eingestempeltSeit = state.eingestempeltSeit
        )
        Spacer(Modifier.height(12.dp))
        if (state.ueberstundenText.isNotEmpty()) {
            val color = if (state.ueberstundenText.startsWith("-")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            val label = if (state.ueberstundenText.startsWith("-")) "Minusstunden" else "Überstunden"
            Text("$label: ${state.ueberstundenText}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
        if (state.startwertAnzeige.isNotEmpty()) {
            val datumText = if (state.standDatumAnzeige.isNotEmpty()) " (${state.standDatumAnzeige})" else ""
            Text("(inkl. Startwert ${state.startwertAnzeige}$datumText)", fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(12.dp))
        UrlaubSection(genommen = state.urlaubGenommen, verbleibend = state.urlaubVerbleibend, gesamt = state.urlaubGesamt)
        Spacer(Modifier.height(12.dp))
        AbschnittTitel("Nächste freie Tage:")

        val aktuelleFeiertage = remember { holeFeiertageBW().sortedBy { it.date } }
        val heute = java.time.LocalDate.now()
        val naechsteFeiertage = remember {
            aktuelleFeiertage.filter { it.date.isAfter(heute) }.take(3)
        }
        val formatter = java.time.format.DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale.GERMAN)

        naechsteFeiertage.forEach {
            val text = "${it.date.format(formatter)} – ${it.description}"
            Text(
                text = text,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

    }
}
@Composable
fun WochenUebersicht(
    stempelListe: List<Stempel>,
    istEingestempelt: Boolean,
    eingestempeltSeit: Date?
) {
    val days = listOf(
        java.time.DayOfWeek.MONDAY,
        java.time.DayOfWeek.TUESDAY,
        java.time.DayOfWeek.WEDNESDAY,
        java.time.DayOfWeek.THURSDAY,
        java.time.DayOfWeek.FRIDAY,
        java.time.DayOfWeek.SATURDAY,
        java.time.DayOfWeek.SUNDAY
    )
    val labels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    val density = LocalDensity.current

    val segmentsByDay = remember(stempelListe, istEingestempelt, eingestempeltSeit) {
        val now = java.time.LocalDate.now()
        val monday = now.with(java.time.DayOfWeek.MONDAY)
        val sunday = now.with(java.time.DayOfWeek.SUNDAY)
        val segments = mutableMapOf<java.time.LocalDate, MutableList<Pair<Float, Float>>>()
        var currentStart: Date? = null
        val sorted = stempelListe.mapNotNull {
            val d = parseDateFlexible(it.zeit)
            if (d != null) d to it.typ else null
        }.sortedBy { it.first.time }
        sorted.forEach { (d, typ) ->
            when (typ) {
                "Start" -> currentStart = d
                "Ende" -> if (currentStart != null && currentStart!!.before(d)) {
                    val start = currentStart!!.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    val end = d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                    var s = start
                    var e = end
                    while (!s.toLocalDate().isAfter(e.toLocalDate())) {
                        val day = s.toLocalDate()
                        val dayStart = s.withHour(0).withMinute(0).withSecond(0).withNano(0)
                        val nextDayStart = dayStart.plusDays(1)
                        val segEnd = if (e.isBefore(nextDayStart)) e else nextDayStart
                        if (!day.isBefore(monday) && !day.isAfter(sunday)) {
                            val startH = s.hour + s.minute / 60f
                            val endH = segEnd.hour + segEnd.minute / 60f
                            segments.getOrPut(day) { mutableListOf() }.add(startH to endH)
                        }
                        s = nextDayStart
                    }
                    currentStart = null
                }
            }
        }
        if (istEingestempelt && eingestempeltSeit != null) {
            val nowDt = Date()
            val start = eingestempeltSeit.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            val end = nowDt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
            val day = start.toLocalDate()
            val weekNow = java.time.LocalDate.now()
            val mondayNow = weekNow.with(java.time.DayOfWeek.MONDAY)
            val sundayNow = weekNow.with(java.time.DayOfWeek.SUNDAY)
            if (!day.isBefore(mondayNow) && !day.isAfter(sundayNow)) {
                val startH = start.hour + start.minute / 60f
                val endH = end.hour + end.minute / 60f
                segments.getOrPut(day) { mutableListOf() }.add(startH to endH)
            }
        }
        segments
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        days.forEachIndexed { idx, dow ->
            val day = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(dow))
            val segs = segmentsByDay[day] ?: emptyList()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(16.dp).padding(vertical = 2.dp)
            ) {
                Text(labels[idx], modifier = Modifier.width(24.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                BoxWithConstraints(
                    modifier = Modifier.weight(1f).height(10.dp).background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    )
                ) {
                    val totalWidth = constraints.maxWidth.toFloat()
                    segs.forEach { (startH, endH) ->
                        val leftPx = (startH.coerceIn(0f, 24f) / 24f) * totalWidth
                        val widthPx = ((endH.coerceIn(0f, 24f) - startH.coerceIn(0f, 24f)).coerceAtLeast(0f) / 24f) * totalWidth
                        Box(
                            modifier = Modifier
                                .offset(x = with(density) { leftPx.toDp() })
                                .width(with(density) { widthPx.toDp() })
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18", "24").forEach {
                Text("$it h", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun ArbeitszeitFortschritt(heute: String, woche: String) {
    val regex = Regex("(\\d+)h\\s*(\\d+)?min?")
    fun parseMinutes(text: String): Int {
        val m = regex.find(text)
        val h = m?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val min = m?.groupValues?.get(2)?.toIntOrNull() ?: 0
        return h * 60 + min
    }

    val minutenHeute = parseMinutes(heute)
    val minutenWoche = parseMinutes(woche)

    val fortschrittHeute = minutenHeute / 480f
    val fortschrittWoche = minutenWoche / 2400f

    val animatedHeute = animateFloatAsState(
        targetValue = fortschrittHeute.coerceAtMost(1f),
        animationSpec = tween(durationMillis = 800)
    ).value
    val animatedWoche = animateFloatAsState(
        targetValue = fortschrittWoche.coerceAtMost(1f),
        animationSpec = tween(durationMillis = 800)
    ).value

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FortschrittKreis("Heute", animatedHeute, fortschrittHeute, 8f)
        FortschrittKreis("Woche", animatedWoche, fortschrittWoche, 40f)
    }
}

@Composable
fun FortschrittKreis(label: String, animated: Float, progress: Float, soll: Float) {
    val color = if (progress < 1f)
        androidx.compose.ui.graphics.lerp(Color.Red, Color(0f, 0.6f, 0f), progress)
    else
        Color(0f, 0.6f, 0f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = animated,
                strokeWidth = 10.dp,
                color = color,
                modifier = Modifier.size(100.dp)
            )
            Text(
                text = String.format("%.0f%%", progress * 100),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "$label: ${String.format("%.1f", progress * soll)}h / ${soll.toInt()}h",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
@Composable
fun UrlaubSection(genommen: Int, verbleibend: Int, gesamt: Int) {
    AbschnittTitel("Urlaub:")
    Text("Genommen: $genommen / $gesamt Tage", fontSize = 16.sp)
    Text(
        "Verbleibend: $verbleibend Tage",
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.secondary
    )
}

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

    var settingsText by remember {
        mutableStateOf(
            if (settingsFile.exists()) settingsFile.readText()
            else "{\n  \"startwertMinuten\": 0,\n  \"standDatum\": \"\",\n  \"homeofficeAktiv\": false\n}"
        )
    }
    var stempelText by remember { mutableStateOf(if (stempelFile.exists()) stempelFile.readText() else "[]") }
    var urlaubText by remember { mutableStateOf(if (urlaubFile.exists()) urlaubFile.readText() else "[]") }

    var statusText by remember { mutableStateOf("") }
    var statusColor by remember { mutableStateOf(primaryColor) }
    var expandedSection by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val meldung = restoreBackup(context, uri)
            statusText = meldung
            statusColor = primaryColor
        } else {
            statusText = "Kein Backup ausgewählt"
            statusColor = errorColor
        }
    }
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

    var vonDatum by remember { mutableStateOf("") }
    var bisDatum by remember { mutableStateOf("") }
    var showVonPicker by remember { mutableStateOf(false) }
    var showBisPicker by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text("Einstellungen", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        // JSON-Dateien Sektion (zusammenklappbar)
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expandedSection = if (expandedSection == "json") null else "json" }
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "JSON-Dateien",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (expandedSection == "json") "▲" else "▼", fontSize = 16.sp)
                }

                if (expandedSection == "json") {
                    Spacer(Modifier.height(8.dp))
                    Text("settings.json", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = settingsText,
                        onValueChange = { settingsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        singleLine = false
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("stempel.json", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = stempelText,
                        onValueChange = { stempelText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        singleLine = false
                    )

                    Spacer(Modifier.height(8.dp))
                    Text("urlaub.json", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = urlaubText,
                        onValueChange = { urlaubText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        singleLine = false
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Urlaub hinzufügen
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Urlaub hinzufügen", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showVonPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (vonDatum.isEmpty()) "Von" else vonDatum, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { showBisPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (bisDatum.isEmpty()) "Bis" else bisDatum, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
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

                                val neuerUrlaub = Urlaubseintrag(vonDatum, bisDatum, tage)
                                urlaubsliste.add(neuerUrlaub)
                                val neuesJson = Gson().toJson(urlaubsliste.sortedBy { it.von })
                                urlaubText = neuesJson
                                urlaubFile.writeText(neuesJson)

                                statusText = "✔ $tage Tage hinzugefügt"
                                statusColor = primaryColor
                                vonDatum = ""
                                bisDatum = ""
                            } else {
                                statusText = "❌ Ungültiges Datum"
                                statusColor = errorColor
                            }
                        } catch (e: Exception) {
                            statusText = "❌ Fehler: ${e.message}"
                            statusColor = errorColor
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) { Text("Hinzufügen") }
            }
        }

        // Date Pickers
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
                dismissButton = {
                    TextButton(onClick = {
                        showVonPicker = false
                    }) { Text("Abbrechen") }
                }
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
                dismissButton = {
                    TextButton(onClick = {
                        showBisPicker = false
                    }) { Text("Abbrechen") }
                }
            ) { DatePicker(state = state) }
        }

        Spacer(Modifier.height(10.dp))

        // Urlaubsliste
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Urlaubsliste (${urlaubsliste.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))

                if (urlaubsliste.isEmpty()) {
                    Text(
                        "Keine Einträge",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                } else {
                    urlaubsliste.sortedBy { it.von }.forEach {
                        Text("${it.von} – ${it.bis} · ${it.tage}T", fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Aktionsbuttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    try {
                        gson.fromJson(settingsText, Einstellungen::class.java)
                        val typeStempel = object : TypeToken<List<Stempel>>() {}.type
                        gson.fromJson<List<Stempel>>(stempelText, typeStempel)
                        val typeUrlaub = object : TypeToken<List<Urlaubseintrag>>() {}.type
                        gson.fromJson<List<Urlaubseintrag>>(urlaubText, typeUrlaub)

                        settingsFile.writeText(settingsText)
                        stempelFile.writeText(stempelText)
                        urlaubFile.writeText(urlaubText)

                        statusText = "✔ Gespeichert"
                        statusColor = primaryColor
                    } catch (e: Exception) {
                        statusText = "❌ Ungültiges JSON"
                        statusColor = errorColor
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) { Text("Speichern") }

            OutlinedButton(onClick = onClose, modifier = Modifier
                .weight(1f)
                .height(48.dp)) {
                Text("Schließen")
            }
        }

        Spacer(Modifier.height(8.dp))
// Aktionsbuttons (Backup + Restore)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    val meldung = backupDateien(context.applicationContext)
                    statusText = meldung
                    statusColor = primaryColor
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) { Text("Backup") }


            OutlinedButton(
                onClick = { launcher.launch("application/zip") },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) { Text("Restore") }

        }
        if (statusText.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(statusText, color = statusColor, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
    }
}