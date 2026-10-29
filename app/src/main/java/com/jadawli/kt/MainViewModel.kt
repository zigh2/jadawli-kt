package com.jadawli.kt

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jadawli.kt.data.AppDb
import com.jadawli.kt.data.BackupImport
import com.jadawli.kt.data.CellEntity
import com.jadawli.kt.data.ColsUi
import com.jadawli.kt.data.GridUi
import com.jadawli.kt.data.NoteEntity
import com.jadawli.kt.data.TableDoc
import com.jadawli.kt.data.TableEntity
import com.jadawli.kt.data.buildGrid
import com.jadawli.kt.data.cellKey
import com.jadawli.kt.data.displayCols
import com.jadawli.kt.data.parseTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val dao = db.dao()
    private val share = SharingStarted.WhileSubscribed(5000)
    private val lock = Mutex()

    val tables: StateFlow<List<TableEntity>> = dao.tables().stateIn(viewModelScope, share, emptyList())
    private val selectedId = MutableStateFlow<Long?>(null)

    val current: StateFlow<TableEntity?> =
        combine(tables, selectedId) { ts, id -> ts.firstOrNull { it.id == id } ?: ts.firstOrNull() }
            .stateIn(viewModelScope, share, null)

    /** null = تلقائي (بطاقة إن كانت الأعمدة أكثر من 8، وإلا جدول). */
    val viewMode = MutableStateFlow<String?>(null)
    /** فهرس السطر في عرض البطاقة؛ null = اليوم. */
    val cardIdx = MutableStateFlow<Int?>(null)

    private var cJson = ""
    private var cDoc = TableDoc(emptyList(), emptyList(), emptySet())
    private var cCols = ColsUi(emptyList())

    private fun docFor(t: TableEntity) {
        if (t.json != cJson) {
            cDoc = parseTable(t.json)
            cCols = ColsUi(displayCols(cDoc))
            cJson = t.json
        }
    }

    /** البناء كله خارج الخيط الرئيسي، ويُعاد استخدام نفس كائن الأعمدة ما دام بنية الجدول لم تتغير. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val grid: StateFlow<GridUi?> = current.flatMapLatest { t ->
        if (t == null) flowOf(null)
        else dao.cells(t.id).combine(dao.rowNotes(t.id)) { l, ns ->
            docFor(t)
            buildGrid(t.id, cDoc, cCols, l, ns.mapNotNull { it.r }.toSet())
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, share, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rowNotes: StateFlow<Map<String, String>> = current.flatMapLatest { t ->
        if (t == null) flowOf(emptyMap())
        else dao.rowNotes(t.id).map { l -> l.associate { (it.r ?: "") to it.text } }
    }.stateIn(viewModelScope, share, emptyMap())

    val noteCount: StateFlow<Int> = dao.noteCount().stateIn(viewModelScope, share, 0)

    fun select(id: Long) { selectedId.value = id; cardIdx.value = null }

    /* ---------------- التراجع والتقدّم (في الذاكرة، كما في جداولي) ---------------- */

    private data class Hist(val kind: String, val t: Long, val r: String, val c: String, val old: String, val nw: String, val at: Long)

    private val undoL = ArrayList<Hist>()
    private val redoL = ArrayList<Hist>()
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo
    val canRedo: StateFlow<Boolean> = _canRedo

    private fun syncHist() { _canUndo.value = undoL.isNotEmpty(); _canRedo.value = redoL.isNotEmpty() }

    private fun pushHist(e: Hist) {
        val l = undoL.lastOrNull()
        if (l != null && l.kind == e.kind && l.t == e.t && l.r == e.r && l.c == e.c && e.at - l.at < 1500) {
            undoL[undoL.size - 1] = l.copy(nw = e.nw, at = e.at)
        } else {
            undoL.add(e)
            if (undoL.size > 200) undoL.removeAt(0)
        }
        redoL.clear()
        syncHist()
    }

    private suspend fun writeCell(t: Long, r: String, c: String, v: String, hist: Boolean) {
        val k = cellKey(t, r, c)
        val old = dao.cellValue(k) ?: ""
        if (hist && old != v) pushHist(Hist("cell", t, r, c, old, v, System.currentTimeMillis()))
        if (v.isEmpty()) dao.deleteCell(k) else dao.upsertCell(CellEntity(k, t, r, c, v, System.currentTimeMillis()))
    }

    private suspend fun writeNote(t: Long, r: String, text: String, hist: Boolean) {
        val old = dao.rowNoteText(t, r) ?: ""
        if (hist && old != text) pushHist(Hist("note", t, r, "", old, text, System.currentTimeMillis()))
        dao.deleteRowNote(t, r)
        if (text.isNotEmpty()) dao.upsertNote(NoteEntity("r|$t|$r", "row", t, r, text, System.currentTimeMillis()))
    }

    fun setCell(t: Long, r: String, c: String, v: String) {
        viewModelScope.launch { lock.withLock { writeCell(t, r, c, v, true) } }
    }

    fun setNote(t: Long, r: String, text: String) {
        viewModelScope.launch { lock.withLock { writeNote(t, r, text, true) } }
    }

    private fun step(isUndo: Boolean) {
        viewModelScope.launch {
            lock.withLock {
                val from = if (isUndo) undoL else redoL
                val to = if (isUndo) redoL else undoL
                if (from.isEmpty()) return@withLock
                val e = from.removeAt(from.size - 1)
                to.add(e)
                val v = if (isUndo) e.old else e.nw
                if (e.kind == "note") writeNote(e.t, e.r, v, false) else writeCell(e.t, e.r, e.c, v, false)
                syncHist()
            }
        }
    }

    fun undo() = step(true)
    fun redo() = step(false)

    /* ---------------- الاستيراد ---------------- */

    suspend fun import(uri: Uri, pass: String, replace: Boolean): String = try {
        val p = BackupImport.read(getApplication(), uri, pass)
        BackupImport.apply(db, p, replace)
        p.settings?.let {
            getApplication<Application>().getSharedPreferences("imported", 0).edit().putString("settings", it.toString()).apply()
        }
        undoL.clear(); redoL.clear(); syncHist()
        "تم استيراد ${p.tables.size} جدولاً و${p.cells.size} خلية و${p.notes.size} ملاحظة"
    } catch (e: BackupImport.WrongPassword) {
        "كلمة السر غير صحيحة"
    } catch (e: Exception) {
        e.message ?: "تعذّر الاستيراد"
    }
}
