package com.jadawli.kt.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.jadawli.kt.data.bump
import com.jadawli.kt.data.showVal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Route {
    object Library : Route
    data class Edit(val id: Long?) : Route
}

@Composable
fun JadawliApp(vm: MainViewModel, sec: com.jadawli.kt.security.Sec) {
    var tab by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf<Route?>(null) }
    BackHandler(enabled = route != null) { route = null }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0; route = null }, icon = { Text("📋") }, label = { Text("البيانات") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1; route = null }, icon = { Text("📈") }, label = { Text("الإحصائيات") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2; route = null }, icon = { Text("🗂️") }, label = { Text("الجداول") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3; route = null }, icon = { Text("⚙️") }, label = { Text("الإعدادات") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val r = route
            if (r is Route.Library) {
                LibraryScreen(vm, onBack = { route = null }, onCreated = { route = null; tab = 0 })
            } else if (r is Route.Edit) {
                EditScreen(vm, r.id, onDone = { route = null })
            } else if (tab == 0) {
                DataScreen(vm)
            } else if (tab == 1) {
                StatsScreen(vm)
            } else if (tab == 2) {
                ManageScreen(vm, onEdit = { route = Route.Edit(it) }, onLibrary = { route = Route.Library }, onGenerated = { tab = 0 })
            } else {
                SettingsScreen(vm, sec)
            }
        }
    }
}

/* ---------------- البيانات ---------------- */

@Composable
fun DataScreen(vm: MainViewModel) {
    val tables by vm.tables.collectAsStateWithLifecycle()
    val cur by vm.current.collectAsStateWithLifecycle()
    val grid by vm.grid.collectAsStateWithLifecycle()
    val notes by vm.rowNotes.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val canRedo by vm.canRedo.collectAsStateWithLifecycle()
    val viewSel by vm.viewMode.collectAsStateWithLifecycle()
    val cardSel by vm.cardIdx.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now().toString() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var noteRow by remember { mutableStateOf<String?>(null) }

    val c = cur
    val gg: GridUi? = if (grid != null && c != null && grid?.tableId == c.id) grid else null
    val latest = rememberUpdatedState(gg)

    val onCell: (String, Int) -> Unit = remember {
        fun(row: String, ci: Int) {
            val g = latest.value ?: return
            val col = g.cols.list.getOrNull(ci) ?: return
            if (col.type == "checkbox") {
                val v = g.rows.firstOrNull { it.name == row }?.vals?.getOrNull(ci) ?: ""
                vm.setCell(g.tableId, row, col.name, if (v == "1") "0" else "1")
            } else {
                edit = Pair(row, ci)
            }
        }
    }
    val onNote: (String) -> Unit = remember { { r: String -> noteRow = r } }

    if (tables.isEmpty() || c == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد جداول بعد.\nاستورد نسخة جداولي الاحتياطية من الإعدادات.", textAlign = TextAlign.Center)
        }
        return
    }

    val isCard = if (viewSel != null) viewSel == "card" else ((gg?.cols?.list?.size ?: 0) > 8)
    val todayIdx = gg?.rows?.indexOfFirst { it.name.contains(today) } ?: -1

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TablePicker(tables, c, vm::select) }
            TextButton(onClick = { vm.undo() }, enabled = canUndo) { Text("↩", fontSize = 18.sp) }
            TextButton(onClick = { vm.redo() }, enabled = canRedo) { Text("↪", fontSize = 18.sp) }
            TextButton(onClick = { vm.viewMode.value = if (isCard) "table" else "card" }) { Text(if (isCard) "☰" else "🗂", fontSize = 18.sp) }
            TextButton(onClick = {
                if (isCard) {
                    vm.cardIdx.value = null
                } else if (todayIdx >= 0) {
                    scope.launch { listState.animateScrollToItem(todayIdx) }
                }
            }) { Text("اليوم") }
        }
        if (gg != null) {
            if (isCard) {
                val n = gg.rows.size
                if (n > 0) {
                    val idx = (cardSel ?: todayIdx.coerceAtLeast(0)).coerceIn(0, n - 1)
                    DayCard(gg, idx, vm, today, notes, { vm.cardIdx.value = it.coerceIn(0, n - 1) }, onCell)
                }
            } else {
                Grid(gg, listState, today, onCell, onNote)
            }
        }
    }

    val e = edit
    if (e != null && gg != null) {
        val col = gg.cols.list.getOrNull(e.second)
        if (col == null) {
            edit = null
        } else {
            val initial = gg.rows.firstOrNull { it.name == e.first }?.vals?.getOrNull(e.second) ?: ""
            if (col.type == "duration" || col.type == "time") {
                HmDialog(
                    title = col.name + " — " + e.first, isDuration = col.type == "duration", initial = initial,
                    onDismiss = { edit = null },
                    onSave = { v -> vm.setCell(gg.tableId, e.first, col.name, v); edit = null }
                )
            } else {
                EditDialog(
                    rowName = e.first, col = col, initial = initial,
                    onDismiss = { edit = null },
                    onSave = { v -> vm.setCell(gg.tableId, e.first, col.name, v); edit = null }
                )
            }
        }
    }
    val nr = noteRow
    if (nr != null && gg != null) {
        NoteDialog(
            rowName = nr, initial = notes[nr] ?: "",
            onDismiss = { noteRow = null },
            onSave = { t -> vm.setNote(gg.tableId, nr, t); noteRow = null }
        )
    }
}

@Composable
fun TablePicker(tables: List<TableEntity>, cur: TableEntity, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Button(onClick = { open = true }) { Text(cur.title + "  ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) }
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

/** شبكة خفيفة: الخلية نص عادي، التعديل بحوار واحد مشترك، وعمود الأسطر ثابت أثناء التمرير الأفقي. */
@Composable
fun Grid(g: GridUi, listState: LazyListState, today: String, onCell: (String, Int) -> Unit, onNote: (String) -> Unit) {
    val scroll = rememberScrollState()
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.height(CELL_H)) {
            HeaderCell("السطر", NAME_W, line)
            Row(Modifier.weight(1f).horizontalScroll(scroll)) {
                g.cols.list.forEach { HeaderCell(it.name, colW(it), line) }
            }
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(g.rows, key = { it.name }) { row ->
                GridRow(row, g.cols, scroll, line, today, onCell, onNote)
            }
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
fun GridRow(
    row: RowUi, cols: ColsUi, scroll: ScrollState, line: Color, today: String,
    onCell: (String, Int) -> Unit, onNote: (String) -> Unit
) {
    val isToday = row.name.contains(today)
    val hi = MaterialTheme.colorScheme.primaryContainer
    Row(Modifier.height(CELL_H)) {
        var nm = Modifier.width(NAME_W).fillMaxHeight()
        if (isToday) nm = nm.background(hi)
        nm = nm.drawBehind {
            drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1f)
            drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
        }.clickable { onNote(row.name) }.padding(horizontal = 6.dp)
        Box(nm, contentAlignment = Alignment.CenterStart) {
            Text((if (row.hasNote) "📝 " else "") + row.name, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
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
        if (col.type == "checkbox") {
            if (value == "1") Text("✓", color = Color(0xFF16A34A), fontSize = 18.sp)
        } else if (value.isNotEmpty()) {
            Text(showVal(col, value), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/* ---------------- بطاقة اليوم ---------------- */

@Composable
fun DayCard(
    g: GridUi, idx: Int, vm: MainViewModel, today: String, notes: Map<String, String>,
    onGo: (Int) -> Unit, onCell: (String, Int) -> Unit
) {
    val row = g.rows[idx]
    val cols = g.cols.list
    val cbIdx = cols.indices.filter { cols[it].type == "checkbox" }
    val done = cbIdx.count { row.vals[it] == "1" }
    var menu by remember { mutableStateOf(false) }
    val anyGroup = cols.any { it.group.isNotEmpty() }

    val stored = notes[row.name] ?: ""
    var nt by remember(row.name, stored) { mutableStateOf(stored) }
    var saved by remember(row.name, stored) { mutableStateOf(stored) }
    val ntS = rememberUpdatedState(nt)
    val savedS = rememberUpdatedState(saved)
    LaunchedEffect(nt, row.name) {
        val v = if (nt.isBlank()) "" else nt
        if (v != saved) {
            delay(500)
            vm.setNote(g.tableId, row.name, v)
            saved = v
        }
    }
    DisposableEffect(row.name) {
        onDispose {
            val v = if (ntS.value.isBlank()) "" else ntS.value
            if (v != savedS.value) vm.setNote(g.tableId, row.name, v)
        }
    }

    Column(
        Modifier.fillMaxSize()
            .pointerInput(idx) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = {
                        if (dx < -150f) {
                            onGo(idx + 1)
                        } else if (dx > 150f) {
                            onGo(idx - 1)
                        }
                    },
                    onHorizontalDrag = { _, d -> dx += d }
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { onGo(idx - 1) }, enabled = idx > 0) { Text("›", fontSize = 24.sp) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TextButton(onClick = { menu = true }) { Text(row.name + "  ▾", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    g.rows.forEachIndexed { i, r -> DropdownMenuItem(text = { Text(r.name) }, onClick = { onGo(i); menu = false }) }
                }
            }
            TextButton(onClick = { onGo(idx + 1) }, enabled = idx < g.rows.size - 1) { Text("‹", fontSize = 24.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📌 " + row.name + (if (row.name.contains(today)) "  • اليوم" else ""), modifier = Modifier.weight(1f), fontSize = 16.sp)
            if (cbIdx.isNotEmpty()) Text("$done / ${cbIdx.size}")
        }
        if (cbIdx.isNotEmpty()) {
            LinearProgressIndicator(progress = { done.toFloat() / cbIdx.size }, modifier = Modifier.fillMaxWidth())
        }

        var last = ""
        cols.forEachIndexed { i, c ->
            val grp = if (c.group.isNotEmpty()) c.group else if (anyGroup) "أخرى" else ""
            if (grp.isNotEmpty() && grp != last) {
                Text(grp, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            }
            last = grp
            FieldRow(c, row.vals[i], i, row.name, onCell) { name, v -> vm.setCell(g.tableId, row.name, name, v) }
        }

        OutlinedTextField(
            value = nt, onValueChange = { nt = it },
            label = { Text("📝 ملاحظة اليوم") }, minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        )
    }
}

@Composable
fun FieldRow(col: Col, value: String, i: Int, rowName: String, onCell: (String, Int) -> Unit, onSet: (String, String) -> Unit) {
    val tint = col.tints[value]
    var m = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    if (tint != null) m = m.background(Color(tint), RoundedCornerShape(8.dp))
    Row(m.padding(horizontal = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(col.name, modifier = Modifier.weight(1f))
        when (col.type) {
            "checkbox" -> Checkbox(checked = value == "1", onCheckedChange = { onSet(col.name, if (it) "1" else "0") })
            "number" -> Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSet(col.name, bump(value, -1.0)) }) { Text("−", fontSize = 20.sp) }
                Text(value.ifEmpty { "0" }, modifier = Modifier.clickable { onCell(rowName, i) }.padding(horizontal = 8.dp))
                TextButton(onClick = { onSet(col.name, bump(value, 1.0)) }) { Text("+", fontSize = 20.sp) }
            }
            else -> TextButton(onClick = { onCell(rowName, i) }) { Text(showVal(col, value).ifEmpty { "—" }) }
        }
    }
}

/* ---------------- الحوارات ---------------- */

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
        val hint = when (col.type) {
            "duration" -> "مثال: 1:30 أو 90 (دقائق)"
            "time" -> "مثال: 14:30"
            "date" -> "مثال: 2026-10-28"
            else -> null
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(col.name + " — " + rowName, fontSize = 14.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Box {
                        OutlinedTextField(
                            value = text, onValueChange = { text = it }, singleLine = true,
                            placeholder = { if (hint != null) Text(hint) },
                            modifier = Modifier.focusRequester(fr),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (col.type == "number") KeyboardType.Number else KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSave(text.trim()) })
                        )
                        LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
                    }
                    if (col.type == "combo") {
                        col.options.forEach { o ->
                            TextButton(onClick = { onSave(o) }, modifier = Modifier.fillMaxWidth()) { Text(o) }
                        }
                    }
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

@Composable
fun NoteDialog(rowName: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📝 " + rowName, fontSize = 14.sp) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

