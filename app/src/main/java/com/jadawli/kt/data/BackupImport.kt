package com.jadawli.kt.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import org.json.JSONObject
import java.io.File

/** استيراد نسخة جداولي الاحتياطية: zip مشفّر AES-256 يحوي backup.json، أو ملف JSON عادي. */
object BackupImport {
    class WrongPassword : Exception()
    class Parsed(val tables: List<JSONObject>, val cells: List<CellEntity>, val notes: List<NoteEntity>, val settings: JSONObject?)

    suspend fun read(ctx: Context, uri: Uri, password: String?): Parsed = withContext(Dispatchers.IO) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("تعذّر فتح الملف")
        val isZip = bytes.size > 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        val text = if (isZip) readZip(ctx, bytes, password) else String(bytes, Charsets.UTF_8)
        parse(text)
    }

    private fun readZip(ctx: Context, bytes: ByteArray, password: String?): String {
        val f = File.createTempFile("imp", ".zip", ctx.cacheDir)
        try {
            f.writeBytes(bytes)
            val z = ZipFile(f)
            if (z.isEncrypted) {
                if (password.isNullOrEmpty()) throw WrongPassword()
                z.setPassword(password.toCharArray())
            }
            val h = z.getFileHeader("backup.json") ?: throw IllegalArgumentException("الملف ليس نسخة احتياطية من جداولي")
            return z.getInputStream(h).use { String(it.readBytes(), Charsets.UTF_8) }
        } catch (e: ZipException) {
            if (e.type == ZipException.Type.WRONG_PASSWORD) throw WrongPassword()
            throw e
        } finally {
            f.delete()
        }
    }

    private fun parse(text: String): Parsed {
        val j = try { JSONObject(text) } catch (e: Exception) { throw IllegalArgumentException("الملف غير صالح") }
        val ts = j.optJSONArray("tables")
        val cs = j.optJSONArray("cells")
        if (ts == null || cs == null) throw IllegalArgumentException("هذه ليست نسخة احتياطية صالحة")
        val now = System.currentTimeMillis()

        val tables = ArrayList<JSONObject>()
        for (i in 0 until ts.length()) {
            val t = ts.getJSONObject(i)
            if (!t.has("id") || !t.has("title") || t.optJSONArray("columns") == null) throw IllegalArgumentException("بنية جدول غير صالحة")
            tables.add(t)
        }
        val cells = ArrayList<CellEntity>()
        for (i in 0 until cs.length()) {
            val o = cs.getJSONObject(i)
            if (o.isNull("v")) continue
            val v = o.get("v").toString()
            if (v.isEmpty()) continue
            val t = o.getLong("t"); val r = o.getString("r"); val c = o.getString("c")
            cells.add(CellEntity(cellKey(t, r, c), t, r, c, v, o.optLong("u", now)))
        }
        val notes = ArrayList<NoteEntity>()
        val ns = j.optJSONArray("notes")
        if (ns != null) for (i in 0 until ns.length()) {
            val n = ns.getJSONObject(i)
            val text2 = n.optString("text", "")
            if (text2.isEmpty()) continue
            notes.add(NoteEntity(n.getString("k"), n.optString("kind", "general"), n.optLong("t", 0), if (n.has("r") && !n.isNull("r")) n.getString("r") else null, text2, n.optLong("u", now)))
        }
        return Parsed(tables, cells, notes, j.optJSONObject("settings"))
    }

    /** replace=true يمسح كل شيء ويحافظ على أرقام الجداول؛ false يضيف الجداول بأرقام جديدة. */
    suspend fun apply(db: AppDb, p: Parsed, replace: Boolean) {
        db.withTransaction {
            val dao = db.dao()
            if (replace) { dao.clearCells(); dao.clearNotes(); dao.clearTables() }
            val base = if (replace) 0L else dao.maxTableId()
            val map = HashMap<Long, Long>()
            val ts = p.tables.mapIndexed { i, o ->
                val old = o.getLong("id")
                val nid = if (replace) old else base + i + 1
                map[old] = nid
                o.put("id", nid)
                TableEntity(nid, o.optString("title", ""), o.toString(), o.optLong("u", 0))
            }
            dao.insertTables(ts)
            dao.insertCells(p.cells.mapNotNull { c ->
                val nt = map[c.t] ?: return@mapNotNull null
                CellEntity(cellKey(nt, c.r, c.c), nt, c.r, c.c, c.v, c.u)
            })
            dao.insertNotes(p.notes.map { n ->
                if (replace) n else n.copy(k = "m$base|${n.k}", t = map[n.t] ?: n.t)
            })
        }
    }
}
