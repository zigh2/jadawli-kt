package com.jadawli.kt.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadawli.kt.MainViewModel
import com.jadawli.kt.data.ColDef
import com.jadawli.kt.data.TInfo
import com.jadawli.kt.data.TableCodec
import com.jadawli.kt.data.TableDef
import com.jadawli.kt.data.Tpl
import kotlinx.coroutines.launch
import java.time.LocalDate

/* ---------------- عناصر مشتركة ---------------- */

@Composable
fun <T> PickerButton(label: String, items: List<Pair<T, String>>, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (k, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onPick(k); open = false }) }
        }
    }
}

private val monthItems = TableCodec.MONTHS.mapIndexed { i, m -> (i + 1) to m }

@Composable
fun SubBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("→ رجوع") }
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

private fun hexColor(h: String): Color = try { Color(android.graphics.Color.parseColor(h)) } catch (e: Exception) { Color.Gray }

@Composable
fun ColorDot(hex: String?, selected: Boolean, onClick: () -> Unit) {
    var m = Modifier.size(26.dp).clip(CircleShape)
    m = if (hex != null) m.background(hexColor(hex)) else m.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    if (selected) m = m.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
    Box(m.clickable { onClick() }, contentAlignment = Alignment.Center) { if (hex == null) Text("∅", fontSize = 12.sp) }
}

@Composable
fun PaletteRow(selected: String?, onPick: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ColorDot(null, selected == null) { onPick(null) }
        TableCodec.PALETTE.forEach { hx -> ColorDot(hx, selected == hx) { onPick(hx) } }
    }
}

/* ---------------- إدارة الجداول ---------------- */

@Composable
fun ManageScreen(vm: MainViewModel, onEdit: (Long?) -> Unit, onLibrary: () -> Unit, onGenerated: () -> Unit) {
    val infos by vm.infos.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val now = remember { LocalDate.now() }
    val real = infos.filter { !it.isTemplate }
    val tpls = infos.filter { it.isTemplate }
    val srcs = real.sortedWith(compareByDescending<TInfo> { it.months.maxOrNull() ?: "" }.thenByDescending { it.id }) + tpls

    var srcId by remember { mutableStateOf<Long?>(null) }
    var month by remember { mutableIntStateOf(now.monthValue) }
    var year by remember { mutableStateOf(now.year.toString()) }
    var name by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    var inited by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var del by remember { mutableStateOf<TInfo?>(null) }

    LaunchedEffect(infos) {
        if (infos.isNotEmpty() && !inited) {
            inited = true
            val nm = if (vm.monthCovered(now.year, now.monthValue)) now.plusMonths(1) else now
            month = nm.monthValue
            year = nm.year.toString()
        }
        if (srcs.none { it.id == srcId }) srcId = srcs.firstOrNull()?.id
    }
    val src = srcs.firstOrNull { it.id == srcId }
    LaunchedEffect(srcId, month, year, infos) {
        if (!touched && src != null) name = vm.uniqueTitle(TableCodec.baseName(src.title) + " " + TableCodec.MONTHS[month - 1] + " " + year)
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("الجداول", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onLibrary) { Text("📚 القوالب") }
                Box(Modifier.width(6.dp))
                Button(onClick = { onEdit(null) }) { Text("＋ جدول") }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("📅 توليد جدول شهري", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (srcs.isEmpty()) {
                    Text("أنشئ جدولاً أو قالباً أولاً ثم عد هنا.")
                } else {
                    PickerButton(
                        (if (src?.isTemplate == true) "قالب: " else "بنية: ") + (src?.title ?: ""),
                        srcs.map { it.id to ((if (it.isTemplate) "قالب: " else "بنية: ") + it.title) },
                        { srcId = it }
                    )
                    OutlinedTextField(value = name, onValueChange = { name = it; touched = true }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PickerButton(TableCodec.MONTHS[month - 1], monthItems, { month = it }, Modifier.weight(1f))
                        OutlinedTextField(
                            value = year, onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                            label = { Text("السنة") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Button(onClick = {
                        val y = year.toIntOrNull() ?: 0
                        val nm = name.trim()
                        val sid = srcId
                        if (sid == null) {
                            msg = "اختر البنية"
                        } else if (nm.isEmpty()) {
                            msg = "أدخل اسم الجدول"
                        } else if (vm.taken(nm)) {
                            msg = "هذا الاسم مستخدم مسبقاً"
                        } else if (y < 2000 || y > 2100) {
                            msg = "السنة غير صحيحة"
                        } else {
                            scope.launch {
                                val id = vm.generateMonth(sid, nm, month, y)
                                if (id > 0) { vm.openTable(id); onGenerated() }
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("🚀 توليد الجدول") }
                    if (msg != null) Text(msg ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item { Text("📊 الجداول", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        if (real.isEmpty()) item { Text("لا توجد جداول.") }
        items(real, key = { it.id }) { t -> TableItem(t, vm, onEdit) { del = t } }
        item { Text("📝 القوالب", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        if (tpls.isEmpty()) item { Text("لا توجد قوالب.") }
        items(tpls, key = { it.id }) { t -> TableItem(t, vm, onEdit) { del = t } }
    }

    val d = del
    if (d != null) {
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("حذف «${d.title}»؟") },
            text = { Text("سيُحذف الجدول وكل بياناته وملاحظاته نهائياً.") },
            confirmButton = { TextButton(onClick = { vm.deleteTable(d.id); del = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun TableItem(t: TInfo, vm: MainViewModel, onEdit: (Long?) -> Unit, onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(t.title, modifier = Modifier.weight(1f).clickable { if (!t.isTemplate) vm.openTable(t.id) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        TextButton(onClick = { onEdit(t.id) }) { Text("✏️") }
        TextButton(onClick = { scope.launch { vm.duplicateTable(t.id) } }) { Text("📄") }
        TextButton(onClick = onDelete) { Text("🗑️") }
    }
}

/* ---------------- محرر الجدول ---------------- */

@Composable
fun EditScreen(vm: MainViewModel, id: Long?, onDone: () -> Unit) {
    var init by remember(id) { mutableStateOf<TableDef?>(null) }
    var missing by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (id == null) {
            val now = LocalDate.now()
            init = TableDef(
                null, "", false,
                TableCodec.keyCols() + ColDef("مهمة", "checkbox", "58"),
                TableCodec.monthRows(now.year, now.monthValue)
            )
        } else {
            val d = vm.tableDef(id)
            if (d == null) missing = true else init = d
        }
    }
    val d = init
    LaunchedEffect(missing) { if (missing) onDone() }
    if (missing) return
    if (d == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("…") }; return }
    EditorBody(d, vm, onDone)
}

@Composable
private fun EditorBody(init: TableDef, vm: MainViewModel, onDone: () -> Unit) {
    var title by remember { mutableStateOf(init.title) }
    var isTpl by remember { mutableStateOf(init.isTemplate) }
    val cols = remember { mutableStateListOf<ColDef>().apply { addAll(init.cols) } }
    val rows = remember { mutableStateListOf<String>().apply { addAll(init.rows) } }
    var editIdx by remember { mutableStateOf<Int?>(null) }
    var bulk by remember { mutableStateOf(false) }
    var monthDlg by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SubBar(if (init.id == null) "جدول جديد" else "تعديل الجدول", onDone) }
        item {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان الجدول") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(Modifier.fillMaxWidth().clickable { isTpl = !isTpl }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isTpl, onCheckedChange = { isTpl = it })
                Text("قالب (بنية فقط، بدون أسطر)")
            }
        }
        item { Text("الأعمدة", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        itemsIndexed(cols) { i, c ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.name + (if (c.key) " 🔑" else ""), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        TableCodec.typeLabel(c.type) + (if (c.group.isNotEmpty()) " • " + c.group else ""),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { if (i > 0) { val t = cols[i]; cols[i] = cols[i - 1]; cols[i - 1] = t } }, enabled = i > 0) { Text("▲") }
                TextButton(onClick = { if (i < cols.size - 1) { val t = cols[i]; cols[i] = cols[i + 1]; cols[i + 1] = t } }, enabled = i < cols.size - 1) { Text("▼") }
                TextButton(onClick = { editIdx = i }) { Text("✏️") }
                TextButton(onClick = { cols.removeAt(i) }) { Text("🗑️") }
            }
        }
        item { OutlinedButton(onClick = { editIdx = -1 }, modifier = Modifier.fillMaxWidth()) { Text("＋ عمود") } }
        item { Text("الأسطر (${rows.size})", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        if (!isTpl) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { bulk = true }) { Text("＋ أسطر") }
                    OutlinedButton(onClick = { monthDlg = true }) { Text("📅 أسطر شهر") }
                }
            }
            itemsIndexed(rows) { i, r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(r, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { rows.removeAt(i) }) { Text("✕") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (err != null) Text(err ?: "", color = MaterialTheme.colorScheme.error)
                Button(onClick = {
                    val t = title.trim()
                    if (t.isEmpty()) {
                        err = "أدخل عنوان الجدول"
                    } else if (vm.taken(t, init.id)) {
                        err = "هذا العنوان مستخدم مسبقاً"
                    } else if (cols.none { !it.key }) {
                        err = "أضف عموداً واحداً على الأقل"
                    } else {
                        scope.launch {
                            vm.saveTable(TableDef(init.id, t, isTpl, cols.toList(), rows.toList()))
                            onDone()
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("💾 حفظ") }
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
            }
        }
    }

    val ei = editIdx
    if (ei != null) {
        val cur = if (ei >= 0 && ei < cols.size) cols[ei] else ColDef(name = "")
        ColEditDialog(
            init = cur,
            others = cols.filterIndexed { i, _ -> i != ei }.map { it.name },
            groups = cols.map { it.group }.filter { it.isNotEmpty() }.distinct(),
            onDismiss = { editIdx = null },
            onSave = { c ->
                if (ei >= 0 && ei < cols.size) cols[ei] = c else cols.add(c)
                editIdx = null
            }
        )
    }
    if (bulk) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { bulk = false },
            title = { Text("إضافة أسطر") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 5, label = { Text("سطر لكل صف") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    text.lines().map { it.trim() }.filter { it.isNotEmpty() && it !in rows }.distinct().forEach { rows.add(it) }
                    bulk = false
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { bulk = false }) { Text("إلغاء") } }
        )
    }
    if (monthDlg) {
        val now = LocalDate.now()
        var m by remember { mutableIntStateOf(now.monthValue) }
        var y by remember { mutableStateOf(now.year.toString()) }
        AlertDialog(
            onDismissRequest = { monthDlg = false },
            title = { Text("أسطر شهر كامل") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerButton(TableCodec.MONTHS[m - 1], monthItems, { m = it }, Modifier.weight(1f))
                    OutlinedTextField(
                        value = y, onValueChange = { y = it.filter { c -> c.isDigit() }.take(4) }, singleLine = true,
                        label = { Text("السنة") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val yy = y.toIntOrNull() ?: 0
                    if (yy in 2000..2100) {
                        TableCodec.monthRows(yy, m).filter { it !in rows }.forEach { rows.add(it) }
                        monthDlg = false
                    }
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { monthDlg = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun ColEditDialog(init: ColDef, others: List<String>, groups: List<String>, onSave: (ColDef) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(init.name) }
    var type by remember { mutableStateOf(init.type) }
    var options by remember { mutableStateOf(init.options) }
    var group by remember { mutableStateOf(init.group) }
    var width by remember { mutableStateOf(init.width) }
    var key by remember { mutableStateOf(init.key) }
    var colors by remember { mutableStateOf(init.colors) }
    var err by remember { mutableStateOf<String?>(null) }
    val listType = type == "select" || type == "combo"
    val opts = options.split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (init.name.isEmpty()) "عمود جديد" else "تعديل العمود", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PickerButton(TableCodec.typeLabel(type), TableCodec.TYPES, { type = it })
                if (listType) {
                    OutlinedTextField(value = options, onValueChange = { options = it }, label = { Text("الخيارات مفصولة بفاصلة") }, modifier = Modifier.fillMaxWidth())
                    if (opts.isNotEmpty()) {
                        Text("ألوان الخيارات", fontSize = 12.sp)
                        opts.forEach { o ->
                            Column {
                                Text(o, fontSize = 13.sp)
                                PaletteRow(colors[o]) { hx -> colors = if (hx == null) colors - o else colors + (o to hx) }
                            }
                        }
                    }
                }
                OutlinedTextField(value = group, onValueChange = { group = it }, label = { Text("المجموعة (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (groups.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        groups.forEach { g -> TextButton(onClick = { group = g }) { Text(g) } }
                    }
                }
                OutlinedTextField(
                    value = width, onValueChange = { width = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("العرض (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Row(Modifier.fillMaxWidth().clickable { key = !key }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = key, onCheckedChange = { key = it })
                    Text("عمود مفتاحي (يُشتق من اسم السطر ولا يُعرض)", fontSize = 12.sp)
                }
                if (err != null) Text(err ?: "", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isEmpty()) {
                    err = "الاسم فارغ"
                } else if (n in others) {
                    err = "الاسم مستخدم في عمود آخر"
                } else {
                    onSave(
                        init.copy(
                            name = n, type = type, width = width.trim(), group = group.trim(), key = key,
                            options = if (listType) opts.joinToString(",") else "",
                            colors = if (listType) colors.filterKeys { it in opts } else emptyMap()
                        )
                    )
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/* ---------------- مكتبة القوالب ---------------- */

@Composable
fun LibraryScreen(vm: MainViewModel, onBack: () -> Unit, onCreated: () -> Unit) {
    val cats = vm.templates.first
    val tpls = vm.templates.second
    var cat by remember { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf<Tpl?>(null) }

    Column(Modifier.fillMaxSize()) {
        SubBar("📚 مكتبة القوالب", onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (cat == null) Button(onClick = { cat = null }) { Text("الكل") } else OutlinedButton(onClick = { cat = null }) { Text("الكل") }
            cats.forEach { c ->
                if (cat == c) Button(onClick = { cat = c }) { Text(c) } else OutlinedButton(onClick = { cat = c }) { Text(c) }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tpls.filter { cat == null || it.cat == cat }, key = { it.id }) { x ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pick = x }.padding(12.dp)
                ) {
                    Text(x.title, fontWeight = FontWeight.Bold)
                    if (x.desc.isNotEmpty()) Text(x.desc, fontSize = 12.sp)
                    Text("${x.cat} • ${x.cols.size} أعمدة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    val x = pick
    if (x != null) PickTplDialog(vm, x, onDismiss = { pick = null }, onDone = { pick = null; onCreated() })
}

@Composable
private fun PickTplDialog(vm: MainViewModel, x: Tpl, onDismiss: () -> Unit, onDone: () -> Unit) {
    val now = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    var month by remember { mutableIntStateOf(now.monthValue) }
    var year by remember { mutableStateOf(now.year.toString()) }
    var name by remember { mutableStateOf("") }
    var touched by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(month, year) {
        if (!touched) name = vm.uniqueTitle(x.title + " " + TableCodec.MONTHS[month - 1] + " " + year)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(x.title, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; touched = true }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerButton(TableCodec.MONTHS[month - 1], monthItems, { month = it }, Modifier.weight(1f))
                    OutlinedTextField(
                        value = year, onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) }, singleLine = true,
                        label = { Text("السنة") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Button(onClick = {
                    val y = year.toIntOrNull() ?: 0
                    val nm = name.trim()
                    if (nm.isEmpty() || vm.taken(nm)) {
                        err = "الاسم فارغ أو مستخدم مسبقاً"
                    } else if (y < 2000 || y > 2100) {
                        err = "السنة غير صحيحة"
                    } else {
                        scope.launch {
                            val id = vm.createFromTemplate(x, nm, month, y, false)
                            vm.openTable(id)
                            onDone()
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("🚀 إنشاء جدول لهذا الشهر") }
                OutlinedButton(onClick = {
                    scope.launch {
                        vm.createFromTemplate(x, vm.uniqueTitle(x.title), month, now.year, true)
                        onDone()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("💾 حفظ كقالب فقط") }
                if (err != null) Text(err ?: "", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
