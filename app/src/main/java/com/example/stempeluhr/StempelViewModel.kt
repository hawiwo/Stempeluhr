package com.example.stempeluhr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class StempelViewModel(app: Application) : AndroidViewModel(app) {

    private val db = StempelDatabase.getDatabase(app)
    private val stempelRepo = StempelRepository(db.stempelDao())
    private val urlaubRepo = UrlaubRepository(db.urlaubDao())

    val stempelListe = stempelRepo.alleEintraege
    val urlaubListe = urlaubRepo.alleEintraege

    private val _state = MutableStateFlow("Bereit")
    val state: StateFlow<String> = _state

    fun addStempel(typ: String, kommentar: String? = null) {
        viewModelScope.launch {
            val zeit = System.currentTimeMillis()
            stempelRepo.add(zeit, typ, kommentar)
            _state.value = "Stempel $typ gespeichert (${Date(zeit)})"
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            stempelRepo.deleteAll()
        }
    }
}
