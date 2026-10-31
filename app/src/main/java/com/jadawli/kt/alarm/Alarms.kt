package com.jadawli.kt.alarm

import android.content.Context
import com.jadawli.kt.data.Col
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AlarmLink(val uid: String, val title: String, val col: String)

/** منبّه كما يراه المستخدم (بنفس صيغة جداولي في النسخ الاحتياطية: days أرقام 0=أحد). */
data class AlarmDef(
    val id: String,
    val title: String = "",
    val time: String = "07:00",
    val days: List<Int> = emptyList(),
    val on: Boolean = true,
    val link: AlarmLink? = null,
    val pre: Int = 0,
    val post: Int = 0,
    val rep: Int = 0,
    val cnt: Int = 0,
    val full: Boolean = true,
    val snooze: Int = 10
)

/** الجدول/السطر/العمود المرتبط بالمنبّه اليوم. */
data class LinkRes(val tableId: Long, val row: String, val col: Col)

/** جدول قابل للربط مع أعمدته (للمحرر). */
data class LinkTable(val uid: String, val title: String, val cols: List<String>)

/** تعارض الاسم بين AlarmDef وبقية الملفات غير وارد؛ هنا التخزين والتحويلات. */
object AlarmStore {
    private val _list = MutableStateFlow<List<AlarmDef>>(emptyList())
    val list: StateFlow<List<AlarmDef>> = _list
    @Volatile private var inited = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("alarms_ui", Context.MODE_PRIVATE)

    fun ensure(c: Context) {
        if (inited) return
        synchronized(this) {
            if (inited) return
            _list.value = parse(prefs(c).getString("list", "[]") ?: "[]")
            inited = true
        }
    }

    fun save(c: Context, l: List<AlarmDef>) {
        ensure(c)
        val s = l.sortedBy { it.time }
        _list.value = s
        prefs(c).edit().putString("list", toJson(s).toString()).apply()
        AlarmEngine.saveConfig(c.applicationContext, nativeJson(s))
        AlarmEngine.scheduleAll(c.applicationContext)
    }

    fun toJson(l: List<AlarmDef>): JSONArray {
        val arr = JSONArray()
        for (a in l) {
            val o = JSONObject()
            o.put("id", a.id); o.put("title", a.title); o.put("time", a.time)
            o.put("days", JSONArray(a.days))
            o.put("on", a.on)
            if (a.link == null) {
                o.put("link", JSONObject.NULL)
            } else {
                val lk = JSONObject()
                lk.put("uid", a.link.uid); lk.put("title", a.link.title); lk.put("col", a.link.col)
                o.put("link", lk)
            }
            o.put("pre", a.pre); o.put("post", a.post); o.put("rep", a.rep); o.put("cnt", a.cnt)
            o.put("full", a.full); o.put("snooze", a.snooze)
            arr.put(o)
        }
        return arr
    }

    fun parse(txt: String): List<AlarmDef> {
        val out = ArrayList<AlarmDef>()
        try {
            val arr = JSONArray(txt)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id", "")
                if (id.isEmpty()) continue
                val ds = ArrayList<Int>()
                o.optJSONArray("days")?.let { d -> for (k in 0 until d.length()) { val v = d.optInt(k, -1); if (v in 0..6) ds.add(v) } }
                val lk = o.optJSONObject("link")
                val link = if (lk == null) null else AlarmLink(lk.optString("uid", ""), lk.optString("title", ""), lk.optString("col", ""))
                out.add(
                    AlarmDef(
                        id, o.optString("title", ""), o.optString("time", "07:00"), ds.distinct().sorted(),
                        o.optBoolean("on", true), link,
                        maxOf(0, o.optInt("pre", 0)), maxOf(0, o.optInt("post", 0)),
                        maxOf(0, o.optInt("rep", 0)), maxOf(0, o.optInt("cnt", 0)),
                        o.optBoolean("full", true),
                        if (o.has("snooze") && !o.isNull("snooze")) o.optInt("snooze", 10) else 10
                    )
                )
            }
        } catch (e: Exception) { /* قائمة تالفة: تُهمل */ }
        return out
    }

    fun mask(days: List<Int>): Int = days.fold(0) { m, d -> m or (1 shl d) }

    /** الصيغة التي يقرؤها المحرك الأصلي (AlarmEngine.load). */
    fun nativeJson(l: List<AlarmDef>): String {
        val arr = JSONArray()
        for (a in l) {
            val o = JSONObject()
            o.put("id", a.id); o.put("title", a.title); o.put("label", a.link?.col ?: "")
            o.put("time", a.time); o.put("days", mask(a.days))
            o.put("pre", a.pre); o.put("post", a.post); o.put("rep", a.rep); o.put("cnt", a.cnt)
            o.put("on", a.on); o.put("full", a.full)
            arr.put(o)
        }
        return arr.toString()
    }

    fun isFilled(col: Col, v: String?): Boolean = if (col.type == "checkbox") v == "1" else !v.isNullOrEmpty()

    fun snoozeMin(a: AlarmDef): Int = maxOf(1, minOf(120, if (a.snooze == 0) 10 else a.snooze))

    val DAYN = listOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")

    fun daysText(a: AlarmDef): String =
        if (a.days.isEmpty() || a.days.size == 7) "كل يوم" else a.days.sorted().joinToString("، ") { DAYN[it] }

    fun fmtTime(t: String, c12: Boolean): String {
        val p = t.split(":")
        val h = p.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        if (!c12) return "%02d:%02d".format(h, m)
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(h12, m, if (h < 12) "ص" else "م")
    }
}

/** تنبيه وصل من إشعار/ملء الشاشة وينتظر العرض في الواجهة: (id, date). */
object AlarmLaunch {
    val pending = MutableStateFlow<Pair<String, String>?>(null)
}
