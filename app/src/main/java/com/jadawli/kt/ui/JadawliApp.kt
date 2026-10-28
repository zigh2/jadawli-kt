package com.jadawli.kt.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadawli.kt.MainViewModel
import com.jadawli.kt.data.Col
import com.jadawli.kt.data.ColsUi
import com.jadawli.kt.data.GridUi
import com.jadawli.kt.data.RowUi
import com.jadawli.kt.data.TableEntity
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun JadawliApp(vm: MainViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("📋") }, label = { Text("البيانات") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("⚙️") }, label = { Text("الإعدادات") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) DataScreen(vm) else SettingsScreen(vm)
        }
    }
}

/* ---------------- البيانات ---------------- */

@Composable
fun DataScreen(vm: MainViewModel) {
    val tables by vm.tables.collectAsStateWithLifecycle()
    val cur by vm.current.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val c = cur
    val g = grid

    if (tables.isEmpty() || c == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد جداول بعد.\nاستورد نسخة جداولي الاحتياطية من الإعدادات.", textAlign = TextAlign.Center)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        TablePicker(tables, c, vm::select)
        if (g != null && g.tableId == c.id) Grid(g, vm)
    }
}

@Composable
fun TablePicker(tables: List<TableEntity>, cur: TableEntity, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Button(onClick = { open = true }) { Text(cur.title + "  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            tables.forEach { t ->
                DropdownMenuItem(text = { Text(t.title) }, onClick = { onPick(t.id); open = false })
            }
        }
    }
}

private val NAME_W = 128.dp
private val CELL_H = 44.dp

private fun colW(c: Col): Dp = c.width.coerceIn(60, 220).dp

/**
 * شبكة خفيفة: الخلية نص عادي فقط (لا حقول ولا Checkbox ولا قوائم لكل خلية).
 * التعديل يتم بحوار واحد مشترك عند الضغط على الخلية، وعمود الأسطر ثابت أثناء التمرير الأفقي.
 */
@Composable
fun Grid(g: GridUi, vm: MainViewModel) {
    val scroll = rememberScrollState()
    val line = MaterialTheme.colorScheme.outlineVariant
    val today = remember { LocalDate.now().toString() }
    var edit by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val latest = rememberUpdatedState(g)

    val onCell: (String, Int) -> Unit = remember {
        fun(row: String, ci: Int) {
            val cur = latest.value
            val col = cur.cols.list.getOrNull(ci) ?: return
            if (col.type == "checkbox") {
                val v = cur.rows.firstOrNull { it.name == row }?.vals?.getOrNull(ci) ?: ""
                vm.setCell(cur.tableId, row, col.name, if (v == "1") "" else "1")
            } else {
                edit = Pair(row, ci)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.height(CELL_H)) {
            HeaderCell("السطر", NAME_W, line)
            Row(Modifier.weight(1f).horizontalScroll(scroll)) {
                g.cols.list.forEach { HeaderCell(it.name, colW(it), line) }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(g.rows, key = { it.name }) { row ->
                GridRow(row, g.cols, scroll, line, today, onCell)
            }
        }
    }

    val e = edit
    if (e != null) {
        val col = g.cols.list.getOrNull(e.second)
        if (col == null) {
            edit = null
        } else {
            val initial = g.rows.firstOrNull { it.name == e.first }?.vals?.getOrNull(e.second) ?: ""
            EditDialog(
                rowName = e.first, col = col, initial = initial,
                onDismiss = { edit = null },
                onSave = { v -> vm.setCell(g.tableId, e.first, col.name, v); edit = null }
            )
        }
    }
}

@Composable
fun HeaderCell(text: String, w: Dp, line: Color) {
    Box(
        Modifier.width(w).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind {
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
            }.padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
}

@Composable
fun GridRow(row: RowUi, cols: ColsUi, scroll: ScrollState, line: Color, today: String, onCell: (String, Int) -> Unit) {
    val isToday = row.name.startsWith(today)
    val hi = MaterialTheme.colorScheme.primaryContainer
    Row(Modifier.height(CELL_H)) {
        Box(
            (if (isToday) Modifier.width(NAME_W).fillMaxHeight().background(hi) else Modifier.width(NAME_W).fillMaxHeight())
                .drawBehind {
                    drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1f)
                    drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
                }.padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) { Text(row.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.weight(1f).horizontalScroll(scroll)) {
            cols.list.forEachIndexed { i, c -> ValueCell(c, row.vals[i], i, row.name, line, onCell) }
        }
    }
}

@Composable
fun ValueCell(col: Col, value: String, idx: Int, rowName: String, line: Color, onCell: (String, Int) -> Unit) {
    val tint = col.tints[value]
    var m = Modifier.width(colW(col)).fillMaxHeight()
    if (tint != null) m = m.background(Color(tint))
    m = m.drawBehind {
        drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1f)
        drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
    }.clickable { onCell(rowName, idx) }
    Box(m, contentAlignment = Alignment.Center) {
        if (value.isNotEmpty()) {
            if (col.type == "checkbox") {
                Text("✓", color = Color(0xFF16A34A), fontSize = 18.sp)
            } else {
                Text(value, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
fun EditDialog(rowName: String, col: Col, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    if (col.type == "select" && col.options.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(col.name + " — " + rowName, fontSize = 14.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    col.options.forEach { o ->
                        TextButton(onClick = { onSave(o) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (o == initial) "● $o" else o)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onSave("") }) { Text("مسح") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
        )
    } else {
        var text by remember { mutableStateOf(initial) }
        val fr = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(col.name + " — " + rowName, fontSize = 14.sp) },
            text = {
                Box {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it }, singleLine = true,
                        modifier = Modifier.focusRequester(fr),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (col.type == "number") KeyboardType.Number else KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onSave(text.trim()) })
                    )
                    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
                }
            },
            confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("حفظ") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { onSave("") }) { Text("مسح") }
                    TextButton(onClick = onDismiss) { Text("إلغاء") }
                }
            }
        )
    }
}

/* ---------------- الإعدادات ---------------- */

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val tables by vm.tables.collectAsStateWithLifecycle()
    val notes by vm.noteCount.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }
    var pass by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(true) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pending = uri; pass = ""; replace = tables.isEmpty() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الإعدادات", fontSize = 22.sp)
        Text("الجداول: ${tables.size}   •   الملاحظات: $notes")
        Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text("♻️ استيراد نسخة احتياطية من جداولي") }
        Text("يقبل الملف المشفّر (zip) الذي أنشأه جداولي، أو ملف JSON.", fontSize = 12.sp)
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("استيراد نسخة احتياطية") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pass, onValueChange = { pass = it }, singleLine = true,
                        label = { Text("كلمة سر النسخة (إن كانت مشفّرة)") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (tables.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !replace, onClick = { replace = false }); Text("إضافتها إلى الموجود")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = replace, onClick = { replace = true }); Text("استبدال كل البيانات الحالية")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val u = pending ?: return@TextButton
                    busy = true
                    scope.launch {
                        msg = vm.import(u, pass, replace)
                        busy = false; pending = null
                    }
                }) { Text(if (busy) "جارٍ الاستيراد…" else "استيراد") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("إلغاء") } }
        )
    }
    if (msg != null) {
        AlertDialog(onDismissRequest = { msg = null }, confirmButton = { TextButton(onClick = { msg = null }) { Text("حسناً") } }, text = { Text(msg ?: "") })
    }
}
