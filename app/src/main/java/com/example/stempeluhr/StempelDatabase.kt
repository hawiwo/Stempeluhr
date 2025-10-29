package com.example.stempeluhr

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [StempelEintrag::class, Urlaubseintrag::class],
    version = 2,
    exportSchema = false
)
abstract class StempelDatabase : RoomDatabase() {
    abstract fun stempelDao(): StempelDao
    abstract fun urlaubDao(): UrlaubDao

    companion object {
        @Volatile private var INSTANCE: StempelDatabase? = null

        fun getDatabase(context: Context): StempelDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    StempelDatabase::class.java,
                    "stempeluhr.db"
                ).build().also { INSTANCE = it }
            }
    }
}
