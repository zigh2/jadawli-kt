package com.jadawli.kt.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/** tints: خيار -> لون ARGB جاهز (يُحسب مرة واحدة عند قراءة الجدول لا عند كل رسم). */
@Immutable
data class Col(
    val name: String, val type: String, val width: Int, val group: String,
    val options: List<String>, val tints: Map<String, Int>
)

@Immutable
data class TableDoc(val columns: List<Col>, val rows: List<String>, val keyCols: Set<String>)

@Immutable
data class ColsUi(val list: List<Col>)

@Immutable
data class RowUi(val name: String, val vals: List<String>, val hasNote: Boolean)

@Immutable
data class GridUi(val tableId: Long, val cols: ColsUi, val rows: List<RowUi>)

private fun strList(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    val out = ArrayList<String>(a.length())
    for (i in 0 until a.length()) out.add(a.optString(i, ""))
    return out
}

private fun optStr(o: JSONObject, k: String): String = if (o.isNull(k)) "" else o.optString(k, "")

fun parseTable(json: String): TableDoc {
    val o = JSONObject(json)
    val cs = o.optJSONArray("columns") ?: JSONArray()
    val cols = ArrayList<Col>()
    for (i in 0 until cs.length()) {
        val c = cs.getJSONObject(i)
        val opts = optStr(c, "options").split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
        val tints = HashMap<String, Int>()
        val co = c.optJSONObject("colors")
        if (co != null) {
            val keys = co.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                try {
                    val rgb = android.graphics.Color.parseColor(co.optString(k, ""))
                    tints[k] = (0x47 shl 24) or (rgb and 0x00FFFFFF)
                } catch (e: Exception) { /* لون غير صالح: يُتجاهل */ }
            }
        }
        val w = optStr(c, "width").toDoubleOrNull()?.toInt() ?: 100
        cols.add(Col(optStr(c, "name"), c.optString("type", "text"), w, optStr(c, "group"), opts, tints))
    }
    return TableDoc(cols, strList(o.optJSONArray("rows")), strList(o.optJSONArray("keyCols")).toSet())
}

/** مثل orderedCols في جداولي: أعمدة المجموعة الواحدة تظهر معاً عند أول ظهور للمجموعة. */
fun displayCols(doc: TableDoc): List<Col> {
    val out = ArrayList<Col>()
    val done = HashSet<String>()
    for (c in doc.columns) {
        if (c.name in done) continue
        val grp = if (c.group.isNotEmpty()) doc.columns.filter { it.group == c.group } else listOf(c)
        for (x in grp) { out.add(x); done.add(x.name) }
    }
    return out.filter { it.name !in doc.keyCols }
}

/** يبني مصفوفة القيم مرة واحدة؛ كل سطر كائن ثابت يُقارَن بمحتواه فلا يُعاد رسم الأسطر التي لم تتغير. */
fun buildGrid(tableId: Long, doc: TableDoc, cols: ColsUi, cells: List<CellEntity>, noteRows: Set<String>): GridUi {
    val ci = HashMap<String, Int>()
    cols.list.forEachIndexed { i, c -> ci[c.name] = i }
    val ri = HashMap<String, Int>()
    doc.rows.forEachIndexed { i, r -> ri[r] = i }
    val vals = Array(doc.rows.size) { Array(cols.list.size) { "" } }
    for (c in cells) {
        val r = ri[c.r] ?: continue
        val k = ci[c.c] ?: continue
        vals[r][k] = c.v
    }
    return GridUi(tableId, cols, doc.rows.mapIndexed { i, n -> RowUi(n, vals[i].asList(), n in noteRows) })
}

/* ---- تنسيق القيم (كما في جداولي) ---- */
private val durRe = Regex("^(\\d+):([0-5]?\\d)$")
private val numRe = Regex("^\\d+(\\.\\d+)?$")

fun parseDur(s: String): Double? {
    val t = s.trim()
    val m = durRe.find(t)
    if (m != null) return m.groupValues[1].toDouble() * 60 + m.groupValues[2].toDouble()
    return if (numRe.matches(t)) t.toDouble() else null
}

fun fmtDur(m: Double): String = "${(m / 60).toInt()}:${Math.round(m % 60).toString().padStart(2, '0')}"

fun showVal(c: Col, x: String): String {
    if (x.isEmpty()) return ""
    if (c.type == "duration") {
        val d = parseDur(x)
        if (d != null) return fmtDur(d)
    }
    return x
}

fun fmtNum(x: Double): String {
    val r = Math.round(x * 1000) / 1000.0
    return if (r == Math.floor(r) && Math.abs(r) < 1e15) r.toLong().toString() else r.toString()
}

fun bump(v: String, d: Double): String = fmtNum((v.toDoubleOrNull() ?: 0.0) + d)
