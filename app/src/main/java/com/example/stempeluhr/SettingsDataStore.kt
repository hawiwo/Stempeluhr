package com.example.stempeluhr

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.edit

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val HOME_OFFICE = booleanPreferencesKey("homeofficeAktiv")
    val STARTWERT_MINUTEN = intPreferencesKey("startwertMinuten")
    val STAND_DATUM = stringPreferencesKey("standDatum")
}

class SettingsRepository(private val context: Context) {

    val homeofficeFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.HOME_OFFICE] ?: false
    }

    val startwertFlow: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.STARTWERT_MINUTEN] ?: 0
    }

    val standDatumFlow: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[SettingsKeys.STAND_DATUM] ?: ""
    }

    suspend fun setHomeoffice(active: Boolean) {
        context.settingsDataStore.edit { it[SettingsKeys.HOME_OFFICE] = active }
    }

    suspend fun setStartwertMinuten(min: Int) {
        context.settingsDataStore.edit { it[SettingsKeys.STARTWERT_MINUTEN] = min }
    }

    suspend fun setStandDatum(text: String) {
        context.settingsDataStore.edit { it[SettingsKeys.STAND_DATUM] = text }
    }
}
