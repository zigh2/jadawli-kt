package com.jadawli.kt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jadawli.kt.data.parseDur
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import com.jadawli.kt.data.Col

/** إدخال الوقت (HH:mm) أو المدة (h:mm) بساعات ودقائق، مع أزرار زيادة سريعة. */
@Composable
fun HmDialog(title: String, isDuration: Boolean, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val start = remember(initial) {
        if (isDuration) {
            val d = parseDur(initial)
            if (d != null) Pair((d / 60).toInt(), Math.round(d % 60).toInt()) else Pair(0, 0)
        } else {
            val mt = Regex("^(\\d{1,2}):(\\d{2})").find(initial.trim())
            if (mt != null) Pair(mt.groupValues[1].toInt(), mt.groupValues[2].toInt()) else Pair(0, 0)
        }
    }
    var h by remember { mutableStateOf(start.first.toString()) }
    var m by remember { mutableStateOf(start.second.toString()) }

    fun set(total: Int) {
        val t = if (isDuration) maxOf(total, 0) else ((total % 1440) + 1440) % 1440
        h = (t / 60).toString()
        m = (t % 60).toString()
    }
    fun addMin(x: Int) { set((h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0) + x) }
    fun commit() {
        val hv = h.toIntOrNull() ?: 0
        val mv = m.toIntOrNull() ?: 0
        val out = if (isDuration) {
            val t = maxOf(hv * 60 + mv, 0)
            "${t / 60}:${(t % 60).toString().padStart(2, '0')}"
        } else {
            hv.coerceIn(0, 23).toString().padStart(2, '0') + ":" + mv.coerceIn(0, 59).toString().padStart(2, '0')
        }
        onSave(out)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 14.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = h, onValueChange = { h = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("ساعات") }, singleLine = true, modifier = Modifier.width(96.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text(":", fontSize = 20.sp)
                    OutlinedTextField(
                        value = m, onValueChange = { m = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("دقائق") }, singleLine = true, modifier = Modifier.width(96.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { addMin(5) }) { Text("+5") }
                    TextButton(onClick = { addMin(15) }) { Text("+15") }
                    TextButton(onClick = { addMin(30) }) { Text("+30") }
                    TextButton(onClick = { addMin(60) }) { Text("+60") }
                }
                if (!isDuration) {
                    TextButton(onClick = { val n = LocalTime.now(); set(n.hour * 60 + n.minute) }) { Text("الآن") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { commit() }) { Text("حفظ") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave("") }) { Text("مسح") }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}

/** منتقي التاريخ: سنة/شهر/يوم مع أزرار «اليوم» و±1 يوم. يحفظ بصيغة yyyy-MM-dd. */
@Composable
fun DateDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val start = remember(initial) { try { LocalDate.parse(initial.trim()) } catch (e: Exception) { LocalDate.now() } }
    var y by remember { mutableStateOf(start.year.toString()) }
    var m by remember { mutableStateOf(start.monthValue.toString()) }
    var d by remember { mutableStateOf(start.dayOfMonth.toString()) }

    fun current(): LocalDate {
        val yy = (y.toIntOrNull() ?: start.year).coerceIn(1900, 2200)
        val mm = (m.toIntOrNull() ?: 1).coerceIn(1, 12)
        val maxD = YearMonth.of(yy, mm).lengthOfMonth()
        return LocalDate.of(yy, mm, (d.toIntOrNull() ?: 1).coerceIn(1, maxD))
    }
    fun set(x: LocalDate) { y = x.year.toString(); m = x.monthValue.toString(); d = x.dayOfMonth.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 14.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = y, onValueChange = { y = it.filter { c -> c.isDigit() }.take(4) }, label = { Text("سنة") },
                        singleLine = true, modifier = Modifier.width(92.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = m, onValueChange = { m = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("شهر") },
                        singleLine = true, modifier = Modifier.width(72.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = d, onValueChange = { d = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("يوم") },
                        singleLine = true, modifier = Modifier.width(72.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { set(current().minusDays(1)) }) { Text("−1 يوم") }
                    TextButton(onClick = { set(current().plusDays(1)) }) { Text("+1 يوم") }
                    TextButton(onClick = { set(LocalDate.now()) }) { Text("اليوم") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current().toString()) }) { Text("حفظ") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave("") }) { Text("مسح") }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
}

/** عرض العمود في الجدول (بالنقاط). */
@Composable
fun WidthDialog(col: Col, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var w by remember { mutableStateOf(col.width.coerceIn(40, 400).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عرض العمود: " + col.name, fontSize = 14.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = w, onValueChange = { w = it.filter { c -> c.isDigit() }.take(3) }, label = { Text("العرض (40 – 400)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(60, 100, 160, 240).forEach { v -> TextButton(onClick = { w = v.toString() }) { Text(v.toString()) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave((w.toIntOrNull() ?: 100).coerceIn(40, 400)) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
