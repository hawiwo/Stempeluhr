package com.example.stempeluhr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StempelRepository(private val dao: StempelDao) {

    val alleEintraege: Flow<List<StempelEintrag>> = dao.getAll()

    suspend fun add(zeitpunkt: Long, typ: String, kommentar: String? = null) {
        withContext(Dispatchers.IO) {
            dao.insert(StempelEintrag(zeitpunkt = zeitpunkt, typ = typ, kommentar = kommentar))
        }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) { dao.deleteAll() }
    }
}

