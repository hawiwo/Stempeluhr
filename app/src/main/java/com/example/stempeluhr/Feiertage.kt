package com.example.feiertage

import java.time.LocalDate
import java.time.Month

data class Feiertag(val date: LocalDate, val description: String)

fun holeFeiertageBW(jahr: Int = LocalDate.now().year): List<Feiertag> {
    val feste = listOf(
        LocalDate.of(jahr, Month.JANUARY, 1) to "Neujahr",
        LocalDate.of(jahr, Month.MAY, 1) to "Tag der Arbeit",
        LocalDate.of(jahr, Month.OCTOBER, 3) to "Tag der Deutschen Einheit",
        LocalDate.of(jahr, Month.NOVEMBER, 1) to "Allerheiligen",
        LocalDate.of(jahr, Month.DECEMBER, 25) to "1. Weihnachtstag",
        LocalDate.of(jahr, Month.DECEMBER, 26) to "2. Weihnachtstag"
    )

    // bewegliche Feiertage berechnen (Ostersonntag + Ableitungen)
    val easter = berechneOstersonntag(jahr)
    val bewegliche = listOf(
        easter.minusDays(2) to "Karfreitag",
        easter.plusDays(1) to "Ostermontag",
        easter.plusDays(39) to "Christi Himmelfahrt",
        easter.plusDays(50) to "Pfingstmontag",
        easter.plusDays(60) to "Fronleichnam"
    )

    return (feste + bewegliche).map { Feiertag(it.first, it.second) }.sortedBy { it.date }
}

private fun berechneOstersonntag(jahr: Int): LocalDate {
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
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(jahr, month, day)
}
