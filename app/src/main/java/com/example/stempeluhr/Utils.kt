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

