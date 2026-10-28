package com.jadawli.kt.data

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONObject

/** tints: خيار -> لون ARGB جاهز (يُحسب مرة واحدة عند قراءة الجدول لا عند كل رسم). */
@Immutable
data class Col(val name: String, val type: String, val width: Int, val options: List<String>, val tints: Map<String, Int>)

@Immutable
data class TableDoc(val columns: List<Col>, val rows: List<String>, val keyCols: Set<String>)

@Immutable
data class ColsUi(val list: List<Col>)

@Immutable
data class RowUi(val name: String, val vals: List<String>)

@Immutable
data class GridUi(val tableId: Long, val cols: ColsUi, val rows: List<RowUi>)

private fun strList(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    val out = ArrayList<String>(a.length())
    for (i in 0 until a.length()) out.add(a.optString(i, ""))
    return out
}

fun parseTable(json: String): TableDoc {
    val o = JSONObject(json)
    val cs = o.optJSONArray("columns") ?: JSONArray()
    val cols = ArrayList<Col>()
    for (i in 0 until cs.length()) {
        val c = cs.getJSONObject(i)
        val opts = c.optString("options", "").split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
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
        val w = c.optString("width", "").toDoubleOrNull()?.toInt() ?: 100
        cols.add(Col(c.optString("name", ""), c.optString("type", "text"), w, opts, tints))
    }
    return TableDoc(cols, strList(o.optJSONArray("rows")), strList(o.optJSONArray("keyCols")).toSet())
}

/** يبني مصفوفة القيم مرة واحدة؛ كل سطر كائن ثابت يُقارَن بمحتواه فلا يُعاد رسم الأسطر التي لم تتغير. */
fun buildGrid(tableId: Long, doc: TableDoc, cols: ColsUi, cells: List<CellEntity>): GridUi {
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
    return GridUi(tableId, cols, doc.rows.mapIndexed { i, n -> RowUi(n, vals[i].asList()) })
}
