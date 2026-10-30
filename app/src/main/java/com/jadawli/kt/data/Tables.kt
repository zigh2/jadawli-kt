package com.jadawli.kt.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** تعريف عمود قابل للتحرير. orig = اسمه الأصلي المحفوظ (لنقل بيانات الخلايا عند إعادة التسمية). */
data class ColDef(
    val name: String,
    val type: String = "text",
    val width: String = "",
    val options: String = "",
    val group: String = "",
    val colors: Map<String, String> = emptyMap(),
    val orig: String? = null,
    val key: Boolean = false
)

data class TableDef(val id: Long?, val title: String, val isTemplate: Boolean, val cols: List<ColDef>, val rows: List<String>)

data class Tpl(val id: String, val cat: String, val title: String, val desc: String, val cols: List<ColDef>)

data class TInfo(val id: Long, val title: String, val isTemplate: Boolean, val months: Set<String>)

object TableCodec {
    val DAYS = listOf("أحد", "إثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت")
    val MONTHS = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    val TYPES = listOf(
        "text" to "نص", "checkbox" to "صح/خطأ", "select" to "قائمة", "combo" to "قائمة مفتوحة",
        "number" to "رقم", "duration" to "مدة (h:mm)", "date" to "تاريخ", "time" to "وقت"
    )
    val PALETTE = listOf("#16a34a", "#65a30d", "#ca8a04", "#ea580c", "#dc2626", "#db2777", "#7c3aed", "#2563eb", "#6b7280")
    val KEY_NAMES = listOf("التاريخ", "اليوم")

    fun typeLabel(t: String): String = TYPES.firstOrNull { it.first == t }?.second ?: t

    fun keyCols(): List<ColDef> = KEY_NAMES.map { ColDef(name = it, key = true) }

    fun monthRows(year: Int, month: Int): List<String> {
        val n = LocalDate.of(year, month, 1).lengthOfMonth()
        return (1..n).map { d ->
            val dt = LocalDate.of(year, month, d)
            val di = dt.dayOfWeek.value % 7   // الأحد = 0
            "%04d-%02d-%02d (%s)".format(year, month, d, DAYS[di])
        }
    }

    private val tailRe = Regex("\\s+(" + MONTHS.joinToString("|") + ")\\s+\\d{4}\$")
    fun baseName(t: String): String {
        val b = tailRe.replace(t.trim(), "")
        return if (b.isEmpty()) t else b
    }

    private fun opt(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "")

    private fun colFrom(c: JSONObject, keys: Set<String>): ColDef {
        val colors = LinkedHashMap<String, String>()
        val co = c.optJSONObject("colors")
        if (co != null) {
            val it = co.keys()
            while (it.hasNext()) { val k = it.next(); colors[k] = co.optString(k, "") }
        }
        val name = opt(c, "name")
        return ColDef(name, c.optString("type", "text"), opt(c, "width"), opt(c, "options"), opt(c, "group"), colors, name, name in keys)
    }

    fun fromJson(json: String, id: Long?): TableDef {
        val o = JSONObject(json)
        val ks = HashSet<String>()
        o.optJSONArray("keyCols")?.let { a -> for (i in 0 until a.length()) ks.add(a.optString(i, "")) }
        val cs = o.optJSONArray("columns") ?: JSONArray()
        val cols = (0 until cs.length()).map { colFrom(cs.getJSONObject(it), ks) }
        val rs = o.optJSONArray("rows") ?: JSONArray()
        val rows = (0 until rs.length()).map { rs.optString(it, "") }
        return TableDef(id, o.optString("title", ""), o.optBoolean("isTemplate", false), cols, rows)
    }

    /** base: كائن الجدول الأصلي إن وُجد، فتبقى حقوله غير المعروفة (uid وغيره). */
    fun toJson(def: TableDef, id: Long, base: JSONObject?): JSONObject {
        val o = base ?: JSONObject()
        o.put("id", id)
        o.put("title", def.title)
        if (o.optString("uid", "").isEmpty()) o.put("uid", UUID.randomUUID().toString())
        val cs = JSONArray()
        for (c in def.cols) {
            val co = JSONObject()
            co.put("name", c.name); co.put("type", c.type); co.put("width", c.width)
            co.put("options", c.options); co.put("group", c.group)
            if (c.colors.isNotEmpty()) {
                val m = JSONObject()
                c.colors.forEach { (k, v) -> m.put(k, v) }
                co.put("colors", m)
            }
            cs.put(co)
        }
        o.put("columns", cs)
        val rs = JSONArray(); def.rows.forEach { rs.put(it) }
        o.put("rows", rs)
        val ks = JSONArray(); def.cols.filter { it.key }.forEach { ks.put(it.name) }
        o.put("keyCols", ks)
        o.put("isTemplate", def.isTemplate)
        o.put("u", System.currentTimeMillis())
        return o
    }

    fun loadTemplates(ctx: Context): Pair<List<String>, List<Tpl>> {
        val txt = ctx.assets.open("templates.json").use { String(it.readBytes(), Charsets.UTF_8) }
        val j = JSONObject(txt)
        val cats = j.getJSONArray("cats").let { a -> (0 until a.length()).map { a.getString(it) } }
        val ta = j.getJSONArray("templates")
        val tpls = (0 until ta.length()).map { i ->
            val t = ta.getJSONObject(i)
            val cs = t.getJSONArray("cols")
            Tpl(t.getString("id"), t.getString("cat"), t.getString("title"), t.optString("desc", ""),
                (0 until cs.length()).map { colFrom(cs.getJSONObject(it), emptySet()).copy(orig = null) })
        }
        return cats to tpls
    }
}
