package com.jadawli.kt.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

object AppScope {
    val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

/** نسخ احتياطي مشفّر إلى Download/Jadawli/Backup (أندرويد 10+): يومياً وعند تغيّر البيانات فقط، مع الاحتفاظ بآخر N نسخة. */
object AutoBackup {
    private const val REL = "Download/Jadawli/Backup/"

    private fun p(c: Context) = c.applicationContext.getSharedPreferences("backup", Context.MODE_PRIVATE)

    fun isOn(c: Context): Boolean = p(c).getBoolean("on", false)
    fun setOn(c: Context, v: Boolean) { p(c).edit().putBoolean("on", v).apply() }
    fun hasPass(c: Context): Boolean = !p(c).getString("pass", "").isNullOrEmpty()
    fun setPass(c: Context, pass: String) { p(c).edit().putString("pass", pass).apply() }
    fun keep(c: Context): Int = p(c).getInt("keep", 30)
    fun setKeep(c: Context, n: Int) { p(c).edit().putInt("keep", maxOf(1, minOf(365, n))).apply() }
    fun lastAt(c: Context): Long = p(c).getLong("lastAt", 0L)
    fun lastWhere(c: Context): String = p(c).getString("lastWhere", "") ?: ""

    /** auto=true: صامت ويتخطى إن لم يلزم. يعيد رسالة للمستخدم أو null إن تُخطّي. */
    suspend fun run(ctx: Context, db: AppDb, auto: Boolean): String? = withContext(Dispatchers.IO) {
        val pr = p(ctx)
        if (auto && !pr.getBoolean("on", false)) return@withContext null
        val pass = pr.getString("pass", "") ?: ""
        if (pass.isEmpty()) return@withContext if (auto) null else "عيّن كلمة سر النسخ الاحتياطي أولاً"
        val dao = db.dao()
        val tables = dao.allTables()
        if (tables.isEmpty()) return@withContext if (auto) null else "لا توجد بيانات لنسخها"
        val mu = maxOf(dao.maxCellU(), maxOf(dao.maxNoteU(), dao.maxTableU()))
        val today = LocalDate.now().toString()
        if (auto && (pr.getString("lastDate", "") == today || mu <= pr.getLong("lastU", 0L))) return@withContext null
        if (Build.VERSION.SDK_INT < 29) {
            return@withContext if (auto) null else "النسخ التلقائي إلى التنزيلات يتطلب أندرويد 10 فأعلى؛ استخدم التصدير اليدوي"
        }
        var tmp: File? = null
        try {
            val json = Exporter.backupJson(tables, dao.allCells(), dao.allNotes(), Exporter.settings(ctx))
            tmp = Exporter.makeZipFile(ctx, json, pass)
            val where = saveQ(ctx, "jadawli-backup-$today.zip", tmp, keep(ctx))
            pr.edit().putString("lastDate", today).putLong("lastU", mu).putLong("lastAt", System.currentTimeMillis()).putString("lastWhere", where).apply()
            "تم النسخ الاحتياطي: $where"
        } catch (e: Exception) {
            if (auto) null else ("تعذّر الحفظ في التنزيلات: " + (e.message ?: ""))
        } finally {
            tmp?.delete()
        }
    }

    @RequiresApi(29)
    private fun saveQ(ctx: Context, name: String, file: File, keep: Int): String {
        val cr = ctx.contentResolver
        val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        // ملف بالاسم نفسه (نسخة اليوم): يُستبدل
        cr.query(
            coll, arrayOf(MediaStore.Downloads._ID),
            MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?",
            arrayOf(name, REL), null
        )?.use { c -> while (c.moveToNext()) cr.delete(ContentUris.withAppendedId(coll, c.getLong(0)), null, null) }
        val v = ContentValues()
        v.put(MediaStore.Downloads.DISPLAY_NAME, name)
        v.put(MediaStore.Downloads.MIME_TYPE, "application/zip")
        v.put(MediaStore.Downloads.RELATIVE_PATH, REL)
        v.put(MediaStore.Downloads.IS_PENDING, 1)
        val uri = cr.insert(coll, v) ?: throw IllegalStateException("رفض النظام إنشاء الملف")
        val os = cr.openOutputStream(uri) ?: throw IllegalStateException("تعذّر فتح الملف للكتابة")
        os.use { o -> file.inputStream().use { it.copyTo(o) } }
        val done = ContentValues()
        done.put(MediaStore.Downloads.IS_PENDING, 0)
        cr.update(uri, done, null, null)
        // الاحتفاظ بآخر keep نسخة فقط
        val old = ArrayList<Long>()
        cr.query(
            coll, arrayOf(MediaStore.Downloads._ID),
            MediaStore.Downloads.RELATIVE_PATH + "=? AND " + MediaStore.Downloads.DISPLAY_NAME + " LIKE ?",
            arrayOf(REL, "jadawli-backup-%.zip"), MediaStore.Downloads.DISPLAY_NAME + " DESC"
        )?.use { c ->
            var i = 0
            while (c.moveToNext()) { i++; if (i > keep) old.add(c.getLong(0)) }
        }
        for (id in old) cr.delete(ContentUris.withAppendedId(coll, id), null, null)
        return REL + name
    }
}
