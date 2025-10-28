package com.example.stempeluhr

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Urlaubseintrag::class], version = 1, exportSchema = false)
abstract class UrlaubDatabase : RoomDatabase() {
    abstract fun urlaubDao(): UrlaubDao

    companion object {
        @Volatile
        private var INSTANCE: UrlaubDatabase? = null

        fun getDatabase(context: Context): UrlaubDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UrlaubDatabase::class.java,
                    "urlaub_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
