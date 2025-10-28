package com.example.stempeluhr

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlaubDao {
    @Query("SELECT * FROM urlaub")
    fun getAll(): Flow<List<Urlaubseintrag>>

    @Insert
    suspend fun insert(eintrag: Urlaubseintrag)

    @Query("DELETE FROM urlaub")
    suspend fun deleteAll()
}
