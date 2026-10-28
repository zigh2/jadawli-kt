package com.jadawli.kt.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadawli.kt.MainViewModel
import com.jadawli.kt.data.Col
import com.jadawli.kt.data.TableEntity
import com.jadawli.kt.data.cellKey
import com.jadawli.kt.data.parseTable
import kotlinx.coroutines.launch

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
    val cells by vm.cells.collectAsStateWithLifecycle()

    if (tables.isEmpty() || cur == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("لا توجد جداول بعد.\nاستورد نسخة جداولي الاحتياطية من الإعدادات.", textAlign = TextAlign.Center)
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        TablePicker(tables, cur!!, vm::select)
        Grid(cur!!, cells, vm)
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

private val ROW_W = 150.dp
private val CELL_H = 44.dp

@Composable
fun Grid(table: TableEntity, cells: Map<String, String>, vm: MainViewModel) {
    val doc = remember(table.json) { parseTable(table.json) }
    val cols = remember(doc) { doc.columns.filter { it.name !in doc.keyCols } }
    val line = MaterialTheme.colorScheme.outlineVariant

    Column(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        Row {
            HeaderCell("السطر", ROW_W)
            cols.forEach { HeaderCell(it.name, it.width.coerceIn(60, 220).dp) }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(doc.rows, key = { it }) { r ->
                Row {
                    Box(Modifier.width(ROW_W).height(CELL_H).border(0.5.dp, line).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                        Text(r, fontSize = 13.sp, maxLines = 2)
                    }
                    cols.forEach { c ->
                        CellView(c, cells[cellKey(table.id, r, c.name)] ?: "", c.width.coerceIn(60, 220).dp) { v ->
                            vm.setCell(table.id, r, c.name, v)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderCell(text: String, w: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(w).height(CELL_H).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center) }
}

@Composable
fun CellView(col: Col, value: String, w: androidx.compose.ui.unit.Dp, onChange: (String) -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val tint: Color? = col.colors[value]?.let { hex ->
        runCatching { Color(android.graphics.Color.parseColor(hex)).copy(alpha = 0.28f) }.getOrNull()
    }
    val base = Modifier.width(w).height(CELL_H).border(0.5.dp, line)
    Box(if (tint != null) base.background(tint) else base, contentAlignment = Alignment.Center) {
        when {
            col.type == "checkbox" ->
                Checkbox(checked = value == "1", onCheckedChange = { onChange(if (it) "1" else "") })
            col.type == "select" && col.options.isNotEmpty() -> SelectCell(col.options, value, onChange)
            else -> TextCell(col.type, value, onChange)
        }
    }
}

@Composable
fun SelectCell(options: List<String>, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().clickable { open = true }, contentAlignment = Alignment.Center) {
        Text(value.ifEmpty { "—" }, fontSize = 13.sp, maxLines = 1)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("—") }, onClick = { onChange(""); open = false })
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false }) }
        }
    }
}

@Composable
fun TextCell(type: String, stored: String, onChange: (String) -> Unit) {
    var text by remember(stored) { mutableStateOf(stored) }
    val kb = if (type == "number") KeyboardType.Number else KeyboardType.Text
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (text != stored) onChange(text) }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            .onFocusChanged { if (!it.isFocused && text != stored) onChange(text) },
        decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { inner() } }
    )
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
