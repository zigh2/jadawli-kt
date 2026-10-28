package com.jadawli.kt

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jadawli.kt.data.AppDb
import com.jadawli.kt.data.BackupImport
import com.jadawli.kt.data.CellEntity
import com.jadawli.kt.data.TableEntity
import com.jadawli.kt.data.cellKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val dao = db.dao()
    private val share = SharingStarted.WhileSubscribed(5000)

    val tables: StateFlow<List<TableEntity>> = dao.tables().stateIn(viewModelScope, share, emptyList())
    private val selectedId = MutableStateFlow<Long?>(null)

    val current: StateFlow<TableEntity?> =
        combine(tables, selectedId) { ts, id -> ts.firstOrNull { it.id == id } ?: ts.firstOrNull() }
            .stateIn(viewModelScope, share, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val cells: StateFlow<Map<String, String>> = current.flatMapLatest { t ->
        if (t == null) flowOf(emptyMap())
        else dao.cells(t.id).map { l -> l.associate { cellKey(it.t, it.r, it.c) to it.v } }
    }.stateIn(viewModelScope, share, emptyMap())

    val noteCount: StateFlow<Int> = dao.noteCount().stateIn(viewModelScope, share, 0)

    fun select(id: Long) { selectedId.value = id }

    fun setCell(t: Long, r: String, c: String, v: String) {
        viewModelScope.launch {
            val k = cellKey(t, r, c)
            if (v.isEmpty()) dao.deleteCell(k) else dao.upsertCell(CellEntity(k, t, r, c, v, System.currentTimeMillis()))
        }
    }

    suspend fun import(uri: Uri, pass: String, replace: Boolean): String = try {
        val p = BackupImport.read(getApplication(), uri, pass)
        BackupImport.apply(db, p, replace)
        p.settings?.let {
            getApplication<Application>().getSharedPreferences("imported", 0).edit().putString("settings", it.toString()).apply()
        }
        "تم استيراد ${p.tables.size} جدولاً و${p.cells.size} خلية و${p.notes.size} ملاحظة"
    } catch (e: BackupImport.WrongPassword) {
        "كلمة السر غير صحيحة"
    } catch (e: Exception) {
        e.message ?: "تعذّر الاستيراد"
    }
}
