package com.example.stempeluhr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import com.example.feiertage.holeFeiertageBW

data class ZeiterfassungState(
    val statusText: String = "Noch nicht eingestempelt",
    val istEingestempelt: Boolean = false,
    val eingestempeltSeit: Date? = null,
    val arbeitsdauerHeute: String = "",
    val arbeitsdauerWoche: String = "",
    val arbeitsdauerMonat: String = "",
    val arbeitsdauerJahr: String = "",
    val ueberstundenText: String = "",
    val startwertAnzeige: String = "",
    val standDatumAnzeige: String = "",
    val homeofficeAktiv: Boolean = false,
    val urlaubGesamt: Int = 30,
    val urlaubGenommen: Int = 0,
    val urlaubVerbleibend: Int = 30,
    val stempelListe: List<Stempel> = emptyList()
)

class StempelViewModel(app: Application) : AndroidViewModel(app) {

    private val gson = Gson()
    private val context = app.applicationContext

    private val logFile = File(context.filesDir, "stempel.json")
    private val settingsFile = File(context.filesDir, "settings.json")
    private val urlaubFile = File(context.filesDir, "urlaub.json")

    private val _state = MutableStateFlow(ZeiterfassungState())
    val state: StateFlow<ZeiterfassungState> = _state

    init {
        viewModelScope.launch {
            ladeAlleDaten()             // erst Daten laden
            refreshCalculationsOnce()   // dann sofort berechnen
            startAutoRefresh()          // danach alle 60s
        }
    }


    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                refreshCalculationsOnce()
            }
        }
    }

    private fun ladeAlleDaten() {
        viewModelScope.launch(Dispatchers.IO) {
            val stempelListe = leseListe<Stempel>(logFile)
            val urlaubsliste = leseListe<Urlaubseintrag>(urlaubFile)

            var homeofficeAktiv = false
            if (settingsFile.exists()) {
                try {
                    val map = gson.fromJson<Map<String, Any>>(
                        settingsFile.readText(),
                        object : TypeToken<Map<String, Any>>() {}.type
                    )
                    homeofficeAktiv = (map["homeofficeAktiv"] as? Boolean) ?: false
                } catch (_: Exception) {}
            }

            val urlaubGenommen = urlaubsliste.sumOf { it.tage }
            val urlaubVerbleibend = 30 - urlaubGenommen

            val letzter = stempelListe.lastOrNull()
            val (eingestempelt, eingestempeltSeit, status) = if (letzter?.typ == "Start") {
                Triple(true, parseDateFlexible(letzter.zeit), "Eingestempelt seit ${letzter.zeit.substring(11)}")
            } else {
                Triple(false, null, "Ausgestempelt")
            }

            _state.value = _state.value.copy(
                stempelListe = stempelListe,
                istEingestempelt = eingestempelt,
                eingestempeltSeit = eingestempeltSeit,
                statusText = status,
                urlaubGenommen = urlaubGenommen,
                urlaubVerbleibend = urlaubVerbleibend,
                homeofficeAktiv = homeofficeAktiv
            )

            // 👉 wenn Daten geladen sind, sofort berechnen
            refreshCalculationsOnce()
        }
    }



    private inline fun <reified T> leseListe(file: File): List<T> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            gson.fromJson<List<T>>(text, object : TypeToken<List<T>>() {}.type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun starteTicker() {
        viewModelScope.launch {
            while (true) {
                try {
                    val s = _state.value
                    val zeiten = berechneAlleZeiten(
                        s.stempelListe.toMutableList(),
                        s.eingestempeltSeit,
                        s.istEingestempelt,
                        context
                    )
                    _state.value = s.copy(
                        arbeitsdauerHeute = zeiten.heute,
                        arbeitsdauerWoche = zeiten.woche,
                        arbeitsdauerMonat = zeiten.monat,
                        arbeitsdauerJahr = zeiten.jahr,
                        ueberstundenText = zeiten.ueberstunden,
                        startwertAnzeige = zeiten.startwert,
                        standDatumAnzeige = zeiten.standDatum
                    )
                } catch (_: Exception) {
                }
                delay(60_000)
            }
        }
    }

    private fun refreshFromDisk() {
        val updated = leseListe<Stempel>(logFile)
        _state.value = _state.value.copy(stempelListe = updated)
    }

    fun refreshCalculationsOnce() {
        viewModelScope.launch {
            val s = _state.value
            val zeiten = berechneAlleZeiten(
                s.stempelListe.toMutableList(),
                s.eingestempeltSeit,
                s.istEingestempelt,
                context
            )
            _state.value = s.copy(
                arbeitsdauerHeute = zeiten.heute,
                arbeitsdauerWoche = zeiten.woche,
                arbeitsdauerMonat = zeiten.monat,
                arbeitsdauerJahr = zeiten.jahr,
                ueberstundenText = zeiten.ueberstunden,
                startwertAnzeige = zeiten.startwert,
                standDatumAnzeige = zeiten.standDatum
            )
        }
    }

    fun toggleStempel() {
        val s = _state.value
        val jetzt = Date()

        if (s.istEingestempelt) {
            addStempel("Ende", s.stempelListe.toMutableList(), gson, logFile, s.homeofficeAktiv)
            refreshFromDisk()
            _state.value = _state.value.copy(
                istEingestempelt = false,
                eingestempeltSeit = null,
                statusText = "Ausgestempelt"
            )
            refreshCalculationsOnce()
        } else {
            addStempel("Start", s.stempelListe.toMutableList(), gson, logFile, s.homeofficeAktiv)
            refreshFromDisk()
            _state.value = _state.value.copy(
                istEingestempelt = true,
                eingestempeltSeit = jetzt,
                statusText = "Eingestempelt seit " + java.text.SimpleDateFormat("HH:mm")
                    .format(jetzt)
            )
            refreshCalculationsOnce()
        }
    }

    fun toggleHomeoffice(aktiv: Boolean) {
        val s = _state.value
        _state.value = s.copy(homeofficeAktiv = aktiv)

        try {
            val jsonText = if (settingsFile.exists()) settingsFile.readText() else "{}"
            val map = Gson().fromJson<MutableMap<String, Any>>(
                jsonText,
                object : com.google.gson.reflect.TypeToken<MutableMap<String, Any>>() {}.type
            ) ?: mutableMapOf()
            map["homeofficeAktiv"] = aktiv
            settingsFile.writeText(Gson().toJson(map))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

