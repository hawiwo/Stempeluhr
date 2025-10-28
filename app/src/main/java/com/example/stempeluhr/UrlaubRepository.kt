package com.example.stempeluhr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class UrlaubRepository(private val dao: UrlaubDao) {

    val alleEintraege: Flow<List<Urlaubseintrag>> = dao.getAll()

    suspend fun add(von: String, bis: String, tage: Int) {
        withContext(Dispatchers.IO) {
            dao.insert(Urlaubseintrag(von = von, bis = bis, tage = tage))
        }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
    }
}
