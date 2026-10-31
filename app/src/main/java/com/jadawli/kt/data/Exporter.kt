package com.jadawli.kt.data

import android.content.Context
import android.net.Uri
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant

/** تصدير بصيغة جداولي نفسها: backup.json داخل zip بتشفير AES-256 (يُفتح بـ 7-Zip) أو JSON عادي. */
object Exporter {
    fun backupJson(tables: List<TableEntity>, cells: List<CellEntity>, notes: List<NoteEntity>, settings: JSONObject): String {
        val root = JSONObject()
        root.put("app", "jadawli")
        root.put("v", 1)
        root.put("exported", Instant.now().toString())
        root.put("settings", settings)
        val ta = JSONArray(); tables.forEach { ta.put(JSONObject(it.json)) }
        root.put("tables", ta)
        val ca = JSONArray()
        cells.forEach { c ->
            val o = JSONObject()
            o.put("t", c.t); o.put("r", c.r); o.put("c", c.c); o.put("v", c.v); o.put("u", c.u)
            ca.put(o)
        }
        root.put("cells", ca)
        val na = JSONArray()
        notes.forEach { n ->
            val o = JSONObject()
            o.put("k", n.k); o.put("kind", n.kind); o.put("t", n.t)
            if (n.r != null) o.put("r", n.r)
            o.put("text", n.text); o.put("u", n.u)
            na.put(o)
        }
        root.put("notes", na)
        return root.toString()
    }

    fun makeZipFile(ctx: Context, json: String, password: String): File {
        val tmp = File.createTempFile("bk", ".zip", ctx.cacheDir)
        tmp.delete()
        val zf = ZipFile(tmp, password.toCharArray())
        val zp = ZipParameters()
        zp.compressionMethod = CompressionMethod.DEFLATE
        zp.isEncryptFiles = true
        zp.encryptionMethod = EncryptionMethod.AES
        zp.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        zp.fileNameInZip = "backup.json"
        zf.addStream(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)), zp)
        return tmp
    }

    fun writeZip(ctx: Context, json: String, password: String, out: Uri) {
        val tmp = makeZipFile(ctx, json, password)
        try {
            val os = ctx.contentResolver.openOutputStream(out) ?: throw IllegalStateException("تعذّر الكتابة في الملف")
            os.use { o -> tmp.inputStream().use { it.copyTo(o) } }
        } finally {
            tmp.delete()
        }
    }

    /** إعدادات النسخة: المظهر والعرض والساعة والمنبهات (بأسماء جداولي). */
    fun settings(ctx: Context): JSONObject {
        val o = com.jadawli.kt.security.Sec.get(ctx).settingsJson()
        com.jadawli.kt.alarm.AlarmStore.ensure(ctx)
        o.put("alarms", com.jadawli.kt.alarm.AlarmStore.toJson(com.jadawli.kt.alarm.AlarmStore.list.value))
        return o
    }

    private fun q(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    private val dateRe = Regex("\\d{4}-\\d{2}-\\d{2}")

    fun csv(def: TableDef, cells: List<CellEntity>): String {
        val m = HashMap<String, String>()
        cells.forEach { m[it.r + "\u0001" + it.c] = it.v }
        val sb = StringBuilder("\uFEFF")
        sb.append((listOf("#") + def.cols.map { it.name }).joinToString(",") { q(it) })
        for (r in def.rows) {
            val line = ArrayList<String>()
            line.add(r)
            for (c in def.cols) {
                var v = m[r + "\u0001" + c.name] ?: ""
                if (v.isEmpty() && c.key) {
                    v = if (c.name == "التاريخ") (dateRe.find(r)?.value ?: "")
                    else if (c.name == "اليوم") (TableCodec.DAYS.firstOrNull { r.contains(it) } ?: "")
                    else ""
                }
                if (c.type == "checkbox") v = if (v == "1") "نعم" else "لا"
                line.add(v)
            }
            sb.append("\r\n").append(line.joinToString(",") { q(it) })
        }
        return sb.toString()
    }
}
