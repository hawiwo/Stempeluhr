package com.example.stempeluhr

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stempel")
data class StempelEintrag(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val zeitpunkt: Long,
    val typ: String,
    val kommentar: String? = null
)
