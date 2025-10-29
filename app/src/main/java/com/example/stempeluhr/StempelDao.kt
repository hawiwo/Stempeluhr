package com.example.stempeluhr

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StempelDao {
    @Query("SELECT * FROM stempel ORDER BY zeitpunkt DESC")
    fun getAll(): Flow<List<StempelEintrag>>

    @Insert
    suspend fun insert(stempel: StempelEintrag)

    @Query("DELETE FROM stempel")
    suspend fun deleteAll()

    @Query("SELECT * FROM stempel ORDER BY zeitpunkt ASC")
    suspend fun getAllOnce(): List<StempelEintrag>
    @Query("SELECT * FROM stempel ORDER BY zeitpunkt ASC")
    suspend fun getAllOnce(): List<StempelEintrag>
}

