package com.jadawli.kt.data

import org.json.JSONArray
import org.json.JSONObject

data class Col(
    val name: String,
    val type: String,
    val width: Int,
    val options: List<String>,
    val colors: Map<String, String>
)

data class TableDoc(val columns: List<Col>, val rows: List<String>, val keyCols: Set<String>)

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
        val colors = HashMap<String, String>()
        val co = c.optJSONObject("colors")
        if (co != null) {
            val it = co.keys()
            while (it.hasNext()) { val k = it.next(); colors[k] = co.optString(k, "") }
        }
        val w = c.optString("width", "").toDoubleOrNull()?.toInt() ?: 100
        cols.add(Col(c.optString("name", ""), c.optString("type", "text"), w, opts, colors))
    }
    return TableDoc(cols, strList(o.optJSONArray("rows")), strList(o.optJSONArray("keyCols")).toSet())
}
