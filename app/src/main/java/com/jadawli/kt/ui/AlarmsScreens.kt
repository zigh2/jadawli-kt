package com.jadawli.kt.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadawli.kt.MainViewModel
import com.jadawli.kt.alarm.AlarmDef
import com.jadawli.kt.alarm.AlarmEngine
import com.jadawli.kt.alarm.AlarmLaunch
import com.jadawli.kt.alarm.AlarmLink
import com.jadawli.kt.alarm.AlarmStore
import com.jadawli.kt.alarm.LinkRes
import com.jadawli.kt.alarm.LinkTable
import com.jadawli.kt.data.TableCodec
import com.jadawli.kt.security.Sec
import com.jadawli.kt.security.findActivity
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** يفتح إعدادات النظام ذات الصلة بالمنبهات: notif | fsi | battery. */
fun openSystemSettings(ctx: Context, which: String) {
    val pkg = ctx.packageName
    val i = when (which) {
        "notif" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        "fsi" -> if (Build.VERSION.SDK_INT >= 34) Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg"))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        "battery" -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
    }
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(i)
    } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (x: Exception) { /* لا شيء */ }
    }
}

private fun notifOn(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

private fun fsiOn(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 34 || AlarmEngine.nm(ctx).canUseFullScreenIntent()

/* ---------------- قائمة المنبهات ---------------- */

@Composable
fun AlarmsScreen(vm: MainViewModel, sec: Sec) {
    val ctx = LocalContext.current
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val c12 by sec.clock12.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf<AlarmDef?>(null) }
    var creating by remember { mutableStateOf(false) }
    var diag by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val t = tick
    val nOn = notifOn(ctx) && t >= 0
    val fOn = fsiOn(ctx) && t >= 0

    fun askPerms() {
        if (!notifOn(ctx) && Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("المنبهات", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = { creating = true }) { Text("＋ منبّه") }
            }
        }
        item { ClockCard(c12) }
        if (!nOn) {
            item {
                Banner("الإشعارات متوقفة فلن ترنّ المنبهات.", "تفعيل") {
                    if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openSystemSettings(ctx, "notif")
                }
            }
        }
        if (!fOn) {
            item { Banner("السماح بالإشعارات بملء الشاشة مطلوب لتظهر شاشة التنبيه فوق القفل.", "السماح") { openSystemSettings(ctx, "fsi") } }
        }
        item { TextButton(onClick = { diag = true }) { Text("🩺 تشخيص وتجربة المنبّه") } }
        if (alarms.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("لا توجد منبهات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("أضف منبهاً وسيرنّ في وقته حتى والتطبيق مغلق.", textAlign = TextAlign.Center)
                }
            }
        }
        items(alarms, key = { it.id }) { a ->
            AlarmCard(a, c12, true, onClick = { edit = a }, onToggle = { on -> vm.saveAlarms(alarms.map { if (it.id == a.id) it.copy(on = on) else it }) })
        }
    }

    if (creating) {
        AlarmEditDialog(null, false, vm, c12,
            onSave = { n -> vm.saveAlarms(alarms + n); creating = false; askPerms() },
            onDelete = null, onDismiss = { creating = false })
    }
    val e = edit
    if (e != null) {
        AlarmEditDialog(e, false, vm, c12,
            onSave = { n -> vm.saveAlarms(alarms.map { if (it.id == n.id) n else it }); edit = null },
            onDelete = { vm.saveAlarms(alarms.filter { it.id != e.id }); edit = null },
            onDismiss = { edit = null })
    }
    if (diag) DiagDialog(onDismiss = { diag = false })
}

@Composable
fun Banner(text: String, btn: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
        OutlinedButton(onClick = onClick) { Text(btn) }
    }
}

@Composable
fun ClockCard(c12: Boolean) {
    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.time.LocalDateTime.now()
            delay(5000)
        }
    }
    val d = now.toLocalDate()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(AlarmStore.fmtTime("${now.hour}:${now.minute}", c12), fontSize = 44.sp, fontWeight = FontWeight.Light)
        Text("${TableCodec.DAYS[d.dayOfWeek.value % 7]}، ${d.dayOfMonth} ${TableCodec.MONTHS[d.monthValue - 1]} ${d.year}", fontSize = 13.sp)
    }
}

@Composable
fun AlarmCard(a: AlarmDef, c12: Boolean, showLink: Boolean, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val link = if (showLink && a.link != null) "🔗 ${a.link.title} ‹ ${a.link.col}" else ""
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            AlarmStore.fmtTime(a.time, c12), fontSize = 26.sp,
            color = if (a.on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(a.title.ifEmpty { "منبّه" }, fontWeight = FontWeight.Bold)
            Text(AlarmStore.daysText(a), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (link.isNotEmpty()) Text(link, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
        Switch(checked = a.on, onCheckedChange = onToggle)
    }
}

/* ---------------- محرر المنبّه ---------------- */

@Composable
private fun DayChip(label: String, on: Boolean, onClick: () -> Unit) {
    val bg = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp)
    ) { Text(label, color = fg, fontSize = 13.sp) }
}

@Composable
private fun NumField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(), onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(4).toIntOrNull() ?: 0) },
        label = { Text(label, fontSize = 12.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun AlarmEditDialog(
    init: AlarmDef?, disguise: Boolean, vm: MainViewModel?, c12: Boolean,
    onSave: (AlarmDef) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(init?.title ?: "") }
    var time by remember { mutableStateOf(init?.time ?: "07:00") }
    var days by remember { mutableStateOf(init?.days ?: emptyList()) }
    var snooze by remember { mutableIntStateOf(init?.snooze ?: 10) }
    var pre by remember { mutableIntStateOf(init?.pre ?: 0) }
    var post by remember { mutableIntStateOf(init?.post ?: 0) }
    var rep by remember { mutableIntStateOf(init?.rep ?: 0) }
    var cnt by remember { mutableIntStateOf(init?.cnt ?: 0) }
    var full by remember { mutableStateOf(init?.full ?: true) }
    var tables by remember { mutableStateOf<List<LinkTable>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var linkIdx by remember { mutableIntStateOf(-1) }
    var linkCol by remember { mutableStateOf(init?.link?.col ?: "") }
    var timeDlg by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!disguise && vm != null) {
            val l = vm.linkables()
            tables = l
            val lk = init?.link
            if (lk != null) {
                var i = l.indexOfFirst { it.uid.isNotEmpty() && it.uid == lk.uid }
                if (i < 0) i = l.indexOfFirst { TableCodec.baseName(it.title) == TableCodec.baseName(lk.title) }
                linkIdx = i
            }
            loaded = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (init == null) "منبّه جديد" else "تعديل المنبّه", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("الوقت", fontSize = 12.sp)
                OutlinedButton(onClick = { timeDlg = true }, modifier = Modifier.fillMaxWidth()) { Text(AlarmStore.fmtTime(time, c12), fontSize = 22.sp) }
                Text("الأيام (بلا تحديد = كل يوم)", fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    AlarmStore.DAYN.forEachIndexed { d, n ->
                        DayChip(n, d in days) { days = if (d in days) days.filter { it != d } else (days + d).sorted() }
                    }
                }
                NumField("التأجيل عند «لاحقاً» (بالدقائق)", snooze) { snooze = it }
                if (!disguise) {
                    Text("ربط بجدول (يذكّرك حتى تسجّل القيمة)", fontSize = 12.sp)
                    val label = if (linkIdx >= 0 && linkIdx < tables.size) tables[linkIdx].title else if (!loaded) (init?.link?.title ?: "بدون ربط") else "بدون ربط"
                    PickerButton(
                        label,
                        listOf(-1 to "بدون ربط") + tables.mapIndexed { i, t -> i to t.title },
                        { i ->
                            linkIdx = i
                            if (i >= 0) {
                                linkCol = tables[i].cols.firstOrNull() ?: ""
                                if (pre == 0 && post == 0 && rep == 0 && cnt == 0) { pre = 30; post = 30; rep = 10; cnt = 3 }
                            }
                        }
                    )
                    if (linkIdx >= 0 && linkIdx < tables.size) {
                        Text("العمود", fontSize = 12.sp)
                        PickerButton(linkCol.ifEmpty { "اختر" }, tables[linkIdx].cols.map { it to it }, { linkCol = it })
                    }
                    Text("إعدادات متقدمة", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    NumField("عدّ تنازلي قبل الموعد (دقيقة)", pre) { pre = it }
                    NumField("استمرار العدّ بعد الموعد (دقيقة)", post) { post = it }
                    NumField("إعادة التنبيه كل (دقيقة)", rep) { rep = it }
                    NumField("عدد مرات الإعادة", cnt) { cnt = it }
                    Row(Modifier.fillMaxWidth().clickable { full = !full }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = full, onCheckedChange = { full = it })
                        Text("تنبيه بملء الشاشة", fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val link: AlarmLink? =
                    if (disguise) init?.link
                    else if (linkIdx >= 0 && linkIdx < tables.size) AlarmLink(tables[linkIdx].uid, tables[linkIdx].title, linkCol)
                    else if (!loaded) init?.link
                    else null
                onSave(
                    AlarmDef(
                        id = init?.id ?: UUID.randomUUID().toString(), title = title.trim(), time = time, days = days,
                        on = init?.on ?: true, link = link, pre = pre, post = post, rep = rep, cnt = cnt, full = full,
                        snooze = maxOf(1, minOf(120, if (snooze == 0) 10 else snooze))
                    )
                )
            }) { Text("حفظ") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            }
        }
    )
    if (timeDlg) {
        HmDialog("وقت المنبّه", false, time, { timeDlg = false }) { v -> if (v.isNotEmpty()) time = v; timeDlg = false }
    }
}

/* ---------------- التشخيص والتجربة ---------------- */

@Composable
fun DiagDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val t = tick
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
    val vol = if (max == 0) 0 else Math.round(am.getStreamVolume(AudioManager.STREAM_ALARM) * 100f / max)
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    val bat = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    val log = remember(t) { AlarmEngine.readLog(ctx).lines().filter { it.isNotBlank() }.take(14).joinToString("\n") }

    fun line(ok: Boolean, s: String) = (if (ok) "✅ " else "⚠️ ") + s

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تشخيص المنبّه", fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(line(notifOn(ctx), if (notifOn(ctx)) "الإشعارات مفعّلة" else "الإشعارات متوقفة"), fontSize = 13.sp)
                Text(line(fsiOn(ctx), if (fsiOn(ctx)) "الإشعار بملء الشاشة مسموح" else "الإشعار بملء الشاشة غير مسموح (لن تستيقظ الشاشة فوق القفل)"), fontSize = 13.sp)
                Text(line(vol > 0, "صوت المنبهات في الهاتف: $vol%"), fontSize = 13.sp)
                Text(line(bat, if (bat) "التطبيق مستثنى من توفير البطارية" else "توفير البطارية قد يؤخر المنبّه في بعض الهواتف"), fontSize = 13.sp)
                Button(onClick = { AlarmEngine.test(ctx, 10); tick++ }, modifier = Modifier.fillMaxWidth()) { Text("🔔 تجربة بعد 10 ثوانٍ (أغلق الشاشة لتجرّب)") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { openSystemSettings(ctx, "notif") }) { Text("الإشعارات") }
                    OutlinedButton(onClick = { openSystemSettings(ctx, "battery") }) { Text("البطارية") }
                    OutlinedButton(onClick = { tick++ }) { Text("تحديث") }
                }
                Text("آخر الأحداث", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(log.ifEmpty { "لا شيء بعد" }, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}

/* ---------------- شاشة التنبيه (عند الرنين) ---------------- */

@Composable
fun AlertHost(vm: MainViewModel, sec: Sec, pend: Pair<String, String>) {
    val ctx = LocalContext.current
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    val c12 by sec.clock12.collectAsStateWithLifecycle()
    val id = pend.first
    val date = pend.second
    val wasLocked = remember { sec.locked.value }
    val now0 = remember { LocalTime.now() }
    val a: AlarmDef? =
        if (id == AlarmEngine.TEST_ID) AlarmDef(id, "تجربة المنبّه", "%02d:%02d".format(now0.hour, now0.minute), snooze = 1)
        else alarms.firstOrNull { it.id == id }

    fun finish() {
        AlarmLaunch.pending.value = null
        if (wasLocked) ctx.findActivity()?.finish()   // التطبيق مقفل: نغلقه كي لا تُكشف بياناته
    }

    LaunchedEffect(id) { AlarmEngine.silenceRing(ctx) }
    if (a == null) {
        LaunchedEffect(id) { finish() }
        return
    }

    var link by remember { mutableStateOf<LinkRes?>(null) }
    var value by remember { mutableStateOf("") }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(a.id) {
        val l = vm.resolveLink(a)
        link = l
        if (l != null) value = vm.cellValue(l.tableId, l.row, l.col.name)
    }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val main = remember(a.time, date) {
        try {
            val hm = a.time.split(":")
            LocalDate.parse(date).atTime(hm[0].trim().toInt(), hm[1].trim().toInt()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) { System.currentTimeMillis() }
    }
    val diff = nowMs - main
    val secs = Math.abs(diff / 1000)
    val counter = "%02d:%02d".format(secs / 60, secs % 60).let { if (diff < 0) "متبقٍ $it" else "+$it" }
    val L = link
    val filled = L == null || AlarmStore.isFilled(L.col, value)

    fun setVal(v: String) {
        val l = link ?: return
        value = v
        vm.setCell(l.tableId, l.row, l.col.name, v)
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text(AlarmStore.fmtTime(a.time, c12), fontSize = 54.sp, fontWeight = FontWeight.Light)
        Text(a.title.ifEmpty { "المنبّه" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(counter, fontSize = 20.sp, color = if (diff >= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
        if (L != null) {
            Text("${L.col.name} — ${L.row}", fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            val col = L.col
            if (col.type == "checkbox") {
                Row(Modifier.clickable { setVal(if (value == "1") "0" else "1") }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = value == "1", onCheckedChange = { setVal(if (it) "1" else "0") })
                    Text("تم", fontSize = 18.sp)
                }
            } else if (col.type == "select" && col.options.isNotEmpty()) {
                PickerButton(value.ifEmpty { "اختر" }, col.options.map { it to it }, { setVal(it) })
            } else {
                OutlinedTextField(
                    value = value, onValueChange = { setVal(it) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = if (col.type == "number") KeyboardType.Number else KeyboardType.Text)
                )
            }
        } else {
            Text("حان موعد المنبّه.", modifier = Modifier.padding(8.dp))
        }
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (L != null) {
                Button(
                    onClick = { AlarmEngine.setDone(ctx, id, date, true); finish() },
                    enabled = filled, modifier = Modifier.fillMaxWidth()
                ) { Text("تم", fontSize = 18.sp) }
            }
            OutlinedButton(
                onClick = { AlarmEngine.snooze(ctx, id, date, AlarmStore.snoozeMin(a)); finish() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("لاحقاً (${AlarmStore.snoozeMin(a)} د)") }
            if (L == null) {
                Button(onClick = { AlarmEngine.stopToday(ctx, id, date); finish() }, modifier = Modifier.fillMaxWidth()) { Text("إيقاف", fontSize = 18.sp) }
            } else if (a.rep > 0 || a.pre > 0) {
                OutlinedButton(onClick = { AlarmEngine.stopToday(ctx, id, date); finish() }, modifier = Modifier.fillMaxWidth()) { Text("إيقاف التذكير اليوم") }
            }
        }
    }
}
