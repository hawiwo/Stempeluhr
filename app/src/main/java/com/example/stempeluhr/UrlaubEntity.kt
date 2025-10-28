package com.example.stempeluhr

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "urlaub")
data class UrlaubEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val von: String,
    val bis: String,
    val tage: Int
)

