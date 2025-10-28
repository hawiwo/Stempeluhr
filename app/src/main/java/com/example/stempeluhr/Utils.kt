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

fun istFeiertagBW(cal: Calendar): Boolean {
    val jahr = cal.get(Calendar.YEAR)
    val easter = berechneOstersonntag(jahr)

    val feste = listOf(
        "01-01", // Neujahr
        "01-06", // Heilige Drei Könige
        "05-01", // Tag der Arbeit
        "10-03", // Tag der Deutschen Einheit
        "11-01", // Allerheiligen
        "12-25", // 1. Weihnachtstag
        "12-26"  // 2. Weihnachtstag
    )

    val bewegliche = listOf(
        addDays(easter, -2),  // Karfreitag
        addDays(easter, 1),   // Ostermontag
        addDays(easter, 39),  // Christi Himmelfahrt
        addDays(easter, 50),  // Pfingstmontag
        addDays(easter, 60)   // Fronleichnam
    )

    val monatTag = String.format("%02d-%02d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))

    if (feste.contains(monatTag)) return true
    return bewegliche.any {
        it.get(Calendar.YEAR) == jahr &&
                it.get(Calendar.MONTH) == cal.get(Calendar.MONTH) &&
                it.get(Calendar.DAY_OF_MONTH) == cal.get(Calendar.DAY_OF_MONTH)
    }
}

fun formatCalToPair(cal: Calendar): Pair<Int, String> {
    return Pair(
        cal.get(Calendar.YEAR),
        String.format("%02d-%02d", cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    )
}

fun addDays(cal: Calendar, days: Int): Calendar {
    val copy = cal.clone() as Calendar
    copy.add(Calendar.DAY_OF_YEAR, days)
    return copy
}

fun berechneOstersonntag(jahr: Int): Calendar {
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
    val cal = Calendar.getInstance()
    cal.set(jahr, monat - 1, tag)
    return cal
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

