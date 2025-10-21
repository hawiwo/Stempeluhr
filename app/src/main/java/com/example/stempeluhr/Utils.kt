package com.example.stempeluhr

import android.content.Context
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

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

