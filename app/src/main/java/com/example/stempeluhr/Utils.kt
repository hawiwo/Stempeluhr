package com.example.stempeluhr

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream


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

fun zipDirectory(directory: File) {
    if (!directory.isDirectory) return

    val zipFile = File(directory.parentFile, "${directory.name}.zip")
    FileOutputStream(zipFile).use { fos ->
        ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
            directory.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(directory).path
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    println("ZIP-Datei erstellt: ${zipFile.absolutePath}")
}
fun backupDateien(context: Context): String {
    val quelleDir = context.filesDir
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val zielDir = File(downloads, "StempeluhrBackup")
    if (!zielDir.exists()) zielDir.mkdirs()

    val zeitstempel = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
    val zipFile = File(zielDir, "backup_$zeitstempel.zip")

    val dateien = listOf(
        File(quelleDir, "stempel.json"),
        File(quelleDir, "settings.json"),
        File(quelleDir, "urlaub.json")
    ).filter { it.exists() }

    if (dateien.isEmpty()) return "Keine Dateien zum Sichern gefunden."

    return try {
        FileOutputStream(zipFile).use { fos ->
            ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                dateien.forEach { file ->
                    zos.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        "Backup erstellt: ${zipFile.absolutePath}"
    } catch (e: Exception) {
        "Fehler beim Backup: ${e.message}"
    }
}

fun restoreBackup(context: Context, zipUri: Uri): String {
    return try {
        val zielDir = context.filesDir
        val input = context.contentResolver.openInputStream(zipUri)
            ?: return "Fehler: ZIP-Datei konnte nicht geöffnet werden"

        ZipInputStream(BufferedInputStream(input)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".json")) {
                    val outFile = File(zielDir, entry.name)
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                entry = zis.nextEntry
            }
        }
        "Restore abgeschlossen"
    } catch (e: Exception) {
        "Fehler beim Restore: ${e.message}"
    }
}

fun formatCalToPair(cal: Calendar): Pair<Int, String> {
    return Pair(
        cal.get(Calendar.YEAR),
        String.format("%02d-%02d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    )
}
fun berechneOstersonntag(jahr: Int): Calendar {
    // Gaußsche Osterformel
    val a = jahr % 19
    val b = jahr / 100
    val c = jahr % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val monat = (h + l - 7 * m + 114) / 31
    val tag = ((h + l - 7 * m + 114) % 31) + 1

    return Calendar.getInstance().apply {
        set(jahr, monat - 1, tag, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

fun addDays(base: Calendar, days: Int): Calendar {
    val cal = base.clone() as Calendar
    cal.add(Calendar.DAY_OF_YEAR, days)
    return cal
}

fun istFeiertagBW(cal: Calendar): Boolean {
    val jahr = cal.get(Calendar.YEAR)
    val easter = berechneOstersonntag(jahr)

    // feste Feiertage
    val feste = setOf(
        Pair(Calendar.JANUARY, 1),   // Neujahr
        Pair(Calendar.JANUARY, 6),   // Heilige Drei Könige
        Pair(Calendar.MAY, 1),       // Tag der Arbeit
        Pair(Calendar.OCTOBER, 3),   // Tag der Deutschen Einheit
        Pair(Calendar.NOVEMBER, 1),  // Allerheiligen
        Pair(Calendar.DECEMBER, 25), // 1. Weihnachtstag
        Pair(Calendar.DECEMBER, 26)  // 2. Weihnachtstag
    )

    if (feste.contains(Pair(cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)))) {
        return true
    }

    // bewegliche Feiertage relativ zu Ostern
    val bewegliche = listOf(
        addDays(easter, -2),  // Karfreitag
        addDays(easter, 1),   // Ostermontag
        addDays(easter, 39),  // Christi Himmelfahrt
        addDays(easter, 50),  // Pfingstmontag
        addDays(easter, 60)   // Fronleichnam (BW!)
    )

    return bewegliche.any {
        it.get(Calendar.YEAR) == jahr &&
                it.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    }
}

fun testBerechnung(context: Context) {
    println("=== TEST STARTE ===")

    val gson = Gson()
    val stempelFile = File(context.filesDir, "stempel.json")

    val stempelListe: List<Stempel> =
        gson.fromJson(stempelFile.readText(), object : TypeToken<List<Stempel>>() {}.type)

    val jetzt = Date()
    val result = berechneAlleZeiten(stempelListe, null, false, context)

    println("=== DEBUG AUSGABE ===")
    //println("Stichtag: ${einstellungen.standDatum}")
    //println("Startwert (Minuten): ${einstellungen.startwertMinuten}")
    println("Heute: ${result.heute}")
    println("Woche: ${result.woche}")
    println("Monat: ${result.monat}")
    println("Jahr: ${result.jahr}")
    println("Überstunden: ${result.ueberstunden}")
    println("Berechnet am: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(jetzt)}")
    println("=====================")
}

