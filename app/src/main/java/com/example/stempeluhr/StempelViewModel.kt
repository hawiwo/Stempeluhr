package com.example.stempeluhr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

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
    private val context = getApplication<Application>().applicationContext
    private val logFile = File(context.filesDir, "stempel.json")

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(ZeiterfassungState())
    val state: kotlinx.coroutines.flow.StateFlow<ZeiterfassungState> = _state

    private val settingsRepo = SettingsRepository(context)
    private val urlaubRepo = UrlaubRepository(UrlaubDatabase.getDatabase(context).urlaubDao())

    init {
        viewModelScope.launch {
            ladeAlleDaten()
            refreshCalculationsOnce()
            startAutoRefresh()
        }
        viewModelScope.launch {
            settingsRepo.homeofficeFlow.collect { aktiv: Boolean ->
                _state.value = _state.value.copy(homeofficeAktiv = aktiv)
            }
        }
        viewModelScope.launch {
            urlaubRepo.alleEintraege.collect { liste ->
                val urlaubGenommen = liste.sumOf { it.tage }
                val urlaubVerbleibend = _state.value.urlaubGesamt - urlaubGenommen
                _state.value = _state.value.copy(
                    urlaubGenommen = urlaubGenommen,
                    urlaubVerbleibend = urlaubVerbleibend
                )
            }
        }
    }

    private suspend fun ladeAlleDaten() {
        withContext(Dispatchers.IO) {
            val stempelListe = leseListe<Stempel>(logFile)
            val letzter = stempelListe.lastOrNull()
            val eingestempelt = letzter?.typ == "Start"
            val eingestempeltSeit = if (eingestempelt) parseDateFlexible(letzter!!.zeit) else null
            val statusText = if (eingestempelt) "Eingestempelt seit ${letzter!!.zeit.substring(11)}" else "Ausgestempelt"
            val startwert = settingsRepo.startwertFlow.first()
            val standDatum = settingsRepo.standDatumFlow.first()
            val homeoffice = settingsRepo.homeofficeFlow.first()

            _state.value = _state.value.copy(
                stempelListe = stempelListe,
                istEingestempelt = eingestempelt,
                eingestempeltSeit = eingestempeltSeit,
                statusText = statusText,
                homeofficeAktiv = homeoffice,
                startwertAnzeige = if (startwert != 0) "$startwert min" else "",
                standDatumAnzeige = standDatum
            )
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

    private fun startAutoRefresh() {
        viewModelScope.launch {
            while (isActive) {
                refreshCalculationsOnce()
                delay(60_000)
            }
        }
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
                statusText = "Eingestempelt seit " + java.text.SimpleDateFormat("HH:mm").format(jetzt)
            )
            refreshCalculationsOnce()
        }
    }

    private fun refreshFromDisk() {
        val updated = leseListe<Stempel>(logFile)
        _state.value = _state.value.copy(stempelListe = updated)
    }

    fun toggleHomeoffice(aktiv: Boolean) {
        viewModelScope.launch {
            settingsRepo.setHomeoffice(aktiv)
        }
    }
}
