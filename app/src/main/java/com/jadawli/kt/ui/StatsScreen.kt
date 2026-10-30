package com.jadawli.kt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadawli.kt.MainViewModel
import com.jadawli.kt.data.GridUi
import com.jadawli.kt.data.fmtDur
import com.jadawli.kt.data.fmtNum
import com.jadawli.kt.data.parseDur
import java.time.LocalDate

data class StatRow(val label: String, val value: String, val pct: Float?, val sub: String)
data class StatSec(val title: String, val rows: List<StatRow>, val d: Int, val n: Int)
data class Stats(val logged: Int, val total: Int, val note: String, val sections: List<StatSec>)

private fun streaks(f: List<Boolean>): Pair<Int, Int> {
    var best = 0
    var run = 0
    for (x in f) { run = if (x) run + 1 else 0; if (run > best) best = run }
    var i = f.size - 1
    if (i >= 0 && !f[i]) i--
    var cur = 0
    while (i >= 0 && f[i]) { cur++; i-- }
    return Pair(best, cur)
}

/** نفس منطق statsFor في جداولي: حتى اليوم إن وُجد في الجدول، وإلا كل الأسطر. */
fun computeStats(g: GridUi, todayIdx: Int): Stats {
    val rows = if (todayIdx >= 0) g.rows.subList(0, todayIdx + 1) else g.rows
    val cols = g.cols.list
    val logged = rows.count { r -> r.vals.any { it.isNotEmpty() && it != "0" } }
    val tn = mapOf("checkbox" to "المهام", "number" to "الأرقام", "duration" to "المدد", "select" to "القوائم", "combo" to "القوائم", "text" to "الملاحظات")
    val secRows = LinkedHashMap<String, MutableList<StatRow>>()
    val secD = HashMap<String, Int>()
    val secN = HashMap<String, Int>()

    cols.forEachIndexed { ci, c ->
        val key = if (c.group.isNotEmpty()) c.group else (tn[c.type] ?: "أخرى")
        val vs = rows.map { it.vals[ci] }
        fun add(r: StatRow) { secRows.getOrPut(key) { ArrayList() }.add(r) }
        when (c.type) {
            "checkbox" -> {
                val f = vs.map { it == "1" }
                val d = f.count { it }
                val st = streaks(f)
                add(StatRow(c.name, "$d / ${f.size}", if (f.isNotEmpty()) d.toFloat() / f.size else 0f,
                    "أطول سلسلة ${st.first}" + (if (todayIdx >= 0) "، الحالية ${st.second}" else "")))
                secD[key] = (secD[key] ?: 0) + d
                secN[key] = (secN[key] ?: 0) + f.size
            }
            "number" -> {
                val n = vs.mapNotNull { it.trim().toDoubleOrNull() }
                if (n.isNotEmpty()) {
                    val sum = n.sum()
                    add(StatRow(c.name, fmtNum(sum), null, "المتوسط ${fmtNum(sum / n.size)}، الأعلى ${fmtNum(n.max())}، في ${n.size} يوم"))
                }
            }
            "duration" -> {
                val m = vs.mapNotNull { parseDur(it) }
                if (m.isNotEmpty()) {
                    val sum = m.sum()
                    add(StatRow(c.name, fmtDur(sum), null, "المتوسط ${fmtDur(sum / m.size)}، في ${m.size} يوم"))
                }
            }
            "select", "combo" -> {
                val filled = vs.filter { it.isNotEmpty() }
                if (filled.isNotEmpty()) {
                    val cnt = LinkedHashMap<String, Int>()
                    filled.forEach { cnt[it] = (cnt[it] ?: 0) + 1 }
                    add(StatRow(c.name, filled.size.toString(), null, cnt.entries.joinToString("، ") { "${it.key}: ${it.value}" }))
                }
            }
            else -> {
                val n = vs.count { it.isNotEmpty() }
                if (n > 0) add(StatRow(c.name, n.toString(), null, "أيام فيها كتابة"))
            }
        }
    }
    val secs = secRows.map { (k, v) -> StatSec(k, v, secD[k] ?: 0, secN[k] ?: 0) }
    return Stats(logged, rows.size, if (todayIdx >= 0) " (حتى اليوم)" else "", secs)
}

@Composable
fun StatsScreen(vm: MainViewModel) {
    val tables by vm.tables.collectAsStateWithLifecycle()
    val cur by vm.current.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val c = cur
    val g = grid
    if (tables.isEmpty() || c == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد جداول.\nأنشئ جدولاً أو استورد بياناتك أولاً.", textAlign = TextAlign.Center)
        }
        return
    }
    val today = remember { LocalDate.now().toString() }
    Column(Modifier.fillMaxSize()) {
        TablePicker(tables, c, vm::select)
        if (g != null && g.tableId == c.id) {
            val st = remember(g) { computeStats(g, g.rows.indexOfFirst { it.name.contains(today) }) }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text("الأيام المسجّلة" + st.note, fontSize = 14.sp)
                        Text("${st.logged} / ${st.total}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { if (st.total > 0) st.logged.toFloat() / st.total else 0f }, modifier = Modifier.fillMaxWidth())
                    }
                }
                items(st.sections, key = { it.title }) { s ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            s.title + (if (s.n > 0) " — ${Math.round(s.d * 100f / s.n)}%" else ""),
                            color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        s.rows.forEach { r ->
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(r.label, fontWeight = FontWeight.Bold)
                                    Text(r.value, fontWeight = FontWeight.Bold)
                                }
                                if (r.pct != null) LinearProgressIndicator(progress = { r.pct }, modifier = Modifier.fillMaxWidth())
                                if (r.sub.isNotEmpty()) Text(r.sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { Box(Modifier.padding(8.dp)) }
            }
        }
    }
}
