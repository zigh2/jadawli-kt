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
import com.jadawli.kt.alarm.AlarmDef
import com.jadawli.kt.alarm.AlarmEngine
import com.jadawli.kt.alarm.AlarmStore
import com.jadawli.kt.alarm.LinkRes
import com.jadawli.kt.alarm.LinkTable
import com.jadawli.kt.data.AutoBackup
import com.jadawli.kt.data.Exporter
import com.jadawli.kt.data.TInfo
import com.jadawli.kt.data.TableCodec
import com.jadawli.kt.data.TableDef
import com.jadawli.kt.data.TableDoc
import com.jadawli.kt.data.Tpl
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
import androidx.room.withTransaction
import org.json.JSONObject
import kotlinx.coroutines.withContext
import com.jadawli.kt.security.Sec

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
    val viewMode = MutableStateFlow<String?>(Sec.get(app).defaultView.value.let { if (it == "table" || it == "card" || it == "all") it else null })
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

    /* ---------------- المنبهات ---------------- */

    val alarms: StateFlow<List<AlarmDef>> = AlarmStore.list
    private val doneCache = HashMap<String, Boolean>()

    init {
        AlarmStore.ensure(app)
        viewModelScope.launch { lock.withLock { syncDone() } }
    }

    fun saveAlarms(l: List<AlarmDef>) {
        AlarmStore.save(getApplication<Application>(), l)
        viewModelScope.launch { lock.withLock { doneCache.clear(); syncDone() } }
    }

    private class Cand(val t: TableEntity, val uid: String, val doc: TableDoc, val row: String)

    private suspend fun resolve(a: AlarmDef): LinkRes? {
        val l = a.link ?: return null
        val today = java.time.LocalDate.now().toString()
        val cands = ArrayList<Cand>()
        for (t in dao.allTables()) {
            val o = JSONObject(t.json)
            if (o.optBoolean("isTemplate", false)) continue
            val d = parseTable(t.json)
            val row = d.rows.firstOrNull { it.contains(today) } ?: continue
            cands.add(Cand(t, o.optString("uid", ""), d, row))
        }
        val has = { c: Cand -> c.doc.columns.any { it.name == l.col } }
        val hit = cands.firstOrNull { it.uid == l.uid && has(it) }
            ?: cands.firstOrNull { TableCodec.baseName(it.t.title) == TableCodec.baseName(l.title) && has(it) }
            ?: return null
        return LinkRes(hit.t.id, hit.row, hit.doc.columns.first { it.name == l.col })
    }

    suspend fun resolveLink(a: AlarmDef): LinkRes? = resolve(a)

    suspend fun cellValue(t: Long, r: String, c: String): String = dao.cellValue(cellKey(t, r, c)) ?: ""

    /** يخبر المحرك أن منبّه اليوم «سُجِّل» (فلا يرنّ) متى امتلأت خلية العمود المرتبط. */
    private suspend fun syncDone() {
        val linked = AlarmStore.list.value.filter { it.link != null }
        if (linked.isEmpty()) return
        val today = java.time.LocalDate.now().toString()
        for (a in linked) {
            val r = resolve(a)
            val done = r != null && AlarmStore.isFilled(r.col, dao.cellValue(cellKey(r.tableId, r.row, r.col.name)))
            val k = a.id + today
            if (doneCache[k] == done) continue
            doneCache[k] = done
            AlarmEngine.setDone(getApplication<Application>(), a.id, today, done)
        }
    }

    /** الجداول القابلة للربط بمنبّه (غير القوالب) مع أعمدتها المعروضة. */
    suspend fun linkables(): List<LinkTable> = withContext(Dispatchers.Default) {
        dao.allTables().mapNotNull { t ->
            val o = JSONObject(t.json)
            if (o.optBoolean("isTemplate", false)) null
            else LinkTable(o.optString("uid", ""), t.title, displayCols(parseTable(t.json)).map { it.name })
        }
    }

    /* ---------------- النسخ الاحتياطي التلقائي ---------------- */

    suspend fun backupNow(auto: Boolean): String? = AutoBackup.run(getApplication<Application>(), db, auto)

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
        syncDone()
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

    /* ---------------- إدارة الجداول والقوالب ---------------- */

    val infos: StateFlow<List<TInfo>> = tables.map { l ->
        l.map { t ->
            val o = JSONObject(t.json)
            val rs = o.optJSONArray("rows")
            val ms = HashSet<String>()
            if (rs != null) for (i in 0 until rs.length()) {
                val r = rs.optString(i, "")
                if (r.length >= 7 && r[4] == '-') ms.add(r.substring(0, 7))
            }
            TInfo(t.id, t.title, o.optBoolean("isTemplate", false), ms)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, share, emptyList())

    val templates: Pair<List<String>, List<Tpl>> by lazy { TableCodec.loadTemplates(getApplication<Application>()) }

    fun taken(title: String, exceptId: Long? = null): Boolean =
        tables.value.any { it.title == title && it.id != exceptId }

    fun uniqueTitle(base: String): String {
        var n = base
        var k = 2
        while (taken(n)) n = "$base (${k++})"
        return n
    }

    fun monthCovered(y: Int, m: Int): Boolean {
        val key = "%04d-%02d".format(y, m)
        return infos.value.any { !it.isTemplate && key in it.months }
    }

    suspend fun tableDef(id: Long): TableDef? = dao.tableById(id)?.let { TableCodec.fromJson(it.json, it.id) }

    suspend fun saveTable(def: TableDef): Long = lock.withLock {
        db.withTransaction {
            val old = def.id?.let { dao.tableById(it) }
            val id = def.id ?: (dao.maxTableId() + 1)
            if (old != null) {
                val oldDef = TableCodec.fromJson(old.json, old.id)
                val keep = def.cols.mapNotNull { it.orig }.toSet()
                for (oc in oldDef.cols) if (oc.name !in keep) dao.deleteColumnCells(id, oc.name)
                val renames = def.cols.filter { it.orig != null && it.orig != it.name }
                renames.forEachIndexed { i, c -> dao.renameColumn(id, c.orig!!, "\u0002tmp$i") }
                renames.forEachIndexed { i, c -> dao.renameColumn(id, "\u0002tmp$i", c.name) }
                val keepRows = def.rows.toSet()
                for (r in oldDef.rows) if (r !in keepRows) { dao.deleteRowCells(id, r); dao.deleteRowNote(id, r) }
            }
            val json = TableCodec.toJson(def, id, old?.let { JSONObject(it.json) })
            dao.insertTables(listOf(TableEntity(id, def.title, json.toString(), System.currentTimeMillis())))
            id
        }
    }

    fun deleteTable(id: Long) {
        viewModelScope.launch {
            lock.withLock {
                db.withTransaction { dao.deleteCellsOfTable(id); dao.deleteNotesOfTable(id); dao.deleteTable(id) }
                undoL.removeAll { it.t == id }
                syncHist()
            }
        }
    }

    suspend fun duplicateTable(id: Long) {
        val d = tableDef(id) ?: return
        var n = d.title + " (نسخة)"
        var k = 2
        while (taken(n)) n = d.title + " (نسخة " + (k++) + ")"
        saveTable(d.copy(id = null, title = n, cols = d.cols.map { it.copy(orig = null) }))
    }

    suspend fun generateMonth(srcId: Long, name: String, m: Int, y: Int): Long {
        val d = tableDef(srcId) ?: return -1
        return saveTable(TableDef(null, name, false, d.cols.map { it.copy(orig = null) }, TableCodec.monthRows(y, m)))
    }

    suspend fun createFromTemplate(x: Tpl, name: String, m: Int, y: Int, asTemplate: Boolean): Long {
        val cols = TableCodec.keyCols() + x.cols
        return saveTable(TableDef(null, name, asTemplate, cols, if (asTemplate) emptyList() else TableCodec.monthRows(y, m)))
    }

    fun openTable(id: Long) { select(id); viewMode.value = null }

    /* ---------------- التصدير ---------------- */

    private fun appCtx(): Application = getApplication<Application>()

    /** password != null: zip مشفّر AES-256، وإلا JSON عادي. */
    suspend fun exportBackup(uri: Uri, password: String?): String = withContext(Dispatchers.IO) {
        try {
            val json = Exporter.backupJson(dao.allTables(), dao.allCells(), dao.allNotes(), Exporter.settings(appCtx()))
            if (password != null) {
                Exporter.writeZip(appCtx(), json, password, uri)
            } else {
                val os = appCtx().contentResolver.openOutputStream(uri) ?: throw IllegalStateException("تعذّر الكتابة في الملف")
                os.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            "تم حفظ النسخة الاحتياطية"
        } catch (e: Exception) {
            e.message ?: "تعذّر التصدير"
        }
    }

    suspend fun exportCsv(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val t = current.value ?: return@withContext "لا يوجد جدول لتصديره"
            val d = TableCodec.fromJson(t.json, t.id)
            val text = Exporter.csv(d, dao.cellsList(t.id))
            val os = appCtx().contentResolver.openOutputStream(uri) ?: throw IllegalStateException("تعذّر الكتابة في الملف")
            os.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            "تم تصدير الجدول"
        } catch (e: Exception) {
            e.message ?: "تعذّر التصدير"
        }
    }

    suspend fun exportPdf(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val t = current.value ?: return@withContext "لا يوجد جدول لتصديره"
            val doc = parseTable(t.json)
            val g = buildGrid(t.id, doc, ColsUi(displayCols(doc)), dao.cellsList(t.id), emptySet())
            val today = java.time.LocalDate.now().toString()
            com.jadawli.kt.data.PdfExport.write(appCtx(), uri, t.title, "${g.rows.size} يوماً  •  $today", g, today)
            "تم تصدير PDF"
        } catch (e: Exception) {
            e.message ?: "تعذّر إنشاء PDF"
        }
    }

    /** يغيّر عرض عمود واحد في بنية الجدول (دون المساس ببقية حقول الجدول). */
    fun setColumnWidth(tableId: Long, colName: String, w: Int) {
        viewModelScope.launch {
            lock.withLock {
                val t = dao.tableById(tableId) ?: return@withLock
                val o = JSONObject(t.json)
                val cs = o.optJSONArray("columns") ?: return@withLock
                for (i in 0 until cs.length()) {
                    val c = cs.getJSONObject(i)
                    if (c.optString("name") == colName) c.put("width", w.toString())
                }
                val now = System.currentTimeMillis()
                o.put("u", now)
                dao.insertTables(listOf(TableEntity(t.id, t.title, o.toString(), now)))
            }
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            lock.withLock {
                db.withTransaction { dao.clearCells(); dao.clearNotes(); dao.clearTables() }
                undoL.clear(); redoL.clear(); syncHist()
            }
        }
    }

    /* ---------------- الاستيراد ---------------- */

    suspend fun import(uri: Uri, pass: String, replace: Boolean): String = try {
        val p = BackupImport.read(getApplication(), uri, pass)
        BackupImport.apply(db, p, replace)
        p.settings?.let {
            val sec = Sec.get(getApplication<Application>())
            val th = it.optString("theme", "")
            if (th == "auto" || th == "light" || th == "dark") sec.setTheme(th)
            val vw = it.optString("view", "")
            if (vw == "table" || vw == "card") sec.setDefaultView(vw)
            if (it.has("clock12")) sec.setClock12(it.optBoolean("clock12", true))
            val al = it.optJSONArray("alarms")
            if (al != null) AlarmStore.save(getApplication<Application>(), AlarmStore.parse(al.toString()))
        }
        undoL.clear(); redoL.clear(); syncHist()
        "تم استيراد ${p.tables.size} جدولاً و${p.cells.size} خلية و${p.notes.size} ملاحظة"
    } catch (e: BackupImport.WrongPassword) {
        "كلمة السر غير صحيحة"
    } catch (e: Exception) {
        e.message ?: "تعذّر الاستيراد"
    }
}
