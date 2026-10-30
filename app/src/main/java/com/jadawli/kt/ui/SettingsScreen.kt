package com.jadawli.kt.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadawli.kt.MainViewModel
import com.jadawli.kt.security.Bio
import com.jadawli.kt.security.Sec
import com.jadawli.kt.security.findActivity
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
private fun SectionTitle(t: String) {
    Text(t, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SwitchRow(label: String, sub: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (sub != null) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel, sec: Sec) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tables by vm.tables.collectAsStateWithLifecycle()
    val noteCount by vm.noteCount.collectAsStateWithLifecycle()
    val theme by sec.theme.collectAsStateWithLifecycle()
    val defView by sec.defaultView.collectAsStateWithLifecycle()
    val lockOn by sec.lockOn.collectAsStateWithLifecycle()
    val bioOn by sec.bioOn.collectAsStateWithLifecycle()
    val disguise by sec.disguiseScreen.collectAsStateWithLifecycle()
    val iconDis by sec.disguiseIcon.collectAsStateWithLifecycle()
    val secure by sec.secure.collectAsStateWithLifecycle()
    val canBio = Bio.can(ctx)
    val today = remember { LocalDate.now().toString() }
    val ver = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }

    var pinFlow by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var exportPassDlg by remember { mutableStateOf(false) }
    var pendingPass by remember { mutableStateOf<String?>(null) }
    var delStep by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var pass by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { pending = uri; pass = ""; replace = tables.isEmpty() }
    }
    val createZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) { scope.launch { msg = vm.exportBackup(uri, pendingPass) } }
    }
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) { scope.launch { msg = vm.exportBackup(uri, null) } }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) { scope.launch { msg = vm.exportCsv(uri) } }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("الإعدادات", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("الجداول: ${tables.size}   •   الملاحظات: $noteCount", fontSize = 12.sp)

        SectionTitle("المظهر")
        Text("وضع الإضاءة", fontSize = 13.sp)
        PickerButton(
            when (theme) { "light" -> "فاتح"; "dark" -> "داكن"; else -> "تلقائي" },
            listOf("auto" to "تلقائي", "light" to "فاتح", "dark" to "داكن"),
            { sec.setTheme(it) }
        )
        Text("العرض الافتراضي للبيانات", fontSize = 13.sp)
        PickerButton(
            when (defView) { "table" -> "جدول"; "card" -> "بطاقة"; else -> "تلقائي (بحسب عدد الأعمدة)" },
            listOf("auto" to "تلقائي (بحسب عدد الأعمدة)", "table" to "جدول", "card" to "بطاقة"),
            { sec.setDefaultView(it); vm.viewMode.value = if (it == "auto") null else it }
        )

        SectionTitle("الأمان")
        SwitchRow("قفل برقم سري", "يطلب الرمز عند فتح التطبيق بعد غيابه 20 ثانية فأكثر", lockOn) { on ->
            if (on) {
                if (sec.hasPin()) { sec.setLockOn(true) } else { pinFlow = "enable" }
            } else {
                pinFlow = "disable"
            }
        }
        SwitchRow(
            "الفتح بالبصمة",
            if (canBio) "بديل سريع عن الرمز، ويبقى الرمز متاحاً دائماً" else "لا توجد بصمة مسجّلة في الجهاز (سجّلها من إعدادات الهاتف)",
            bioOn, enabled = lockOn && canBio
        ) { on ->
            if (on) {
                val act = ctx.findActivity()
                if (act != null) { Bio.prompt(act, onOk = { sec.setBioOn(true) }, onFail = { }) }
            } else {
                sec.setBioOn(false)
            }
        }
        if (sec.hasPin()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pinFlow = "change" }) { Text("تغيير الرمز") }
                OutlinedButton(onClick = { pinFlow = "remove" }) { Text("حذف الرمز") }
            }
        }
        SwitchRow("شاشة التمويه", "يظهر التطبيق كتطبيق منبّه عند فتحه. اضغط مطولاً على أيقونة الساعة ⏰ للدخول", disguise) { sec.setDisguiseScreen(it) }
        SwitchRow("تمويه الأيقونة والاسم", "يظهر في قائمة التطبيقات باسم «المنبّه» وبأيقونة ساعة (قد يتأخر تغيير الأيقونة لحظات)", iconDis) { on ->
            if (sec.setIconDisguise(on)) {
                msg = if (on) "ستظهر أيقونة «المنبّه» بعد لحظات" else "عادت الأيقونة العادية"
            } else {
                msg = "تعذّر تغيير الأيقونة"
            }
        }
        SwitchRow("إخفاء المحتوى في الشاشات الأخيرة", "يمنع لقطات الشاشة ويحجب معاينة التطبيق في قائمة التطبيقات الأخيرة", secure) { sec.setSecure(it) }
        Text("القفل والتمويه يخفيان الواجهة فقط، ولا يشفّران البيانات المخزّنة على الجهاز.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("النسخ الاحتياطي والتصدير")
        Button(onClick = { exportPassDlg = true }, modifier = Modifier.fillMaxWidth()) { Text("⬇️ نسخة احتياطية مشفّرة (zip)") }
        OutlinedButton(onClick = { createJson.launch("jadawli-backup-$today.json") }, modifier = Modifier.fillMaxWidth()) { Text("نسخة احتياطية JSON (غير مشفّرة)") }
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("⬆️ استيراد نسخة احتياطية") }
        OutlinedButton(onClick = { createCsv.launch("jadawli-$today.csv") }, modifier = Modifier.fillMaxWidth()) { Text("تصدير الجدول الحالي (CSV)") }
        Text("النسخة المشفّرة بصيغة جداولي نفسها (AES-256) وتُفتح بـ 7-Zip، ويقبلها جداولي القديم.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionTitle("البيانات")
        OutlinedButton(onClick = { delStep = 1 }, modifier = Modifier.fillMaxWidth()) { Text("🗑️ حذف كل البيانات", color = MaterialTheme.colorScheme.error) }

        SectionTitle("عن التطبيق")
        Text("الإصدار: $ver", fontSize = 13.sp)
    }

    /* ---- حوارات الرمز ---- */
    when (pinFlow) {
        "enable", "change2" -> PinSetDialog(
            onDone = { pin ->
                sec.setPin(pin)
                if (pinFlow == "enable") sec.setLockOn(true)
                pinFlow = null
            },
            onCancel = { pinFlow = null }
        )
        "change", "remove", "disable" -> PinDialog(
            sec, "أدخل الرمز الحالي",
            onOk = {
                when (pinFlow) {
                    "change" -> { pinFlow = "change2" }
                    "remove" -> { sec.removePin(); pinFlow = null }
                    else -> { sec.setLockOn(false); pinFlow = null }
                }
            },
            onCancel = { pinFlow = null }
        )
        else -> {}
    }

    /* ---- كلمة سر النسخة المشفّرة ---- */
    if (exportPassDlg) {
        ExportPassDialog(
            onOk = { pw ->
                exportPassDlg = false
                pendingPass = pw
                createZip.launch("jadawli-backup-$today.zip")
            },
            onCancel = { exportPassDlg = false }
        )
    }

    /* ---- الاستيراد ---- */
    val pu = pending
    if (pu != null) {
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
                    busy = true
                    scope.launch {
                        msg = vm.import(pu, pass, replace)
                        busy = false
                        pending = null
                    }
                }) { Text(if (busy) "جارٍ الاستيراد…" else "استيراد") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("إلغاء") } }
        )
    }

    /* ---- حذف كل البيانات ---- */
    if (delStep == 1) {
        AlertDialog(
            onDismissRequest = { delStep = 0 },
            title = { Text("حذف كل البيانات؟") },
            text = { Text("سيتم حذف كل الجداول والبيانات والملاحظات من هذا الجهاز نهائياً. تأكد من وجود نسخة احتياطية.") },
            confirmButton = { TextButton(onClick = { delStep = 2 }) { Text("متابعة") } },
            dismissButton = { TextButton(onClick = { delStep = 0 }) { Text("إلغاء") } }
        )
    }
    if (delStep == 2) {
        ChallengeDialog(
            onOk = { vm.deleteAll(); delStep = 0; msg = "تم حذف كل البيانات" },
            onCancel = { delStep = 0 }
        )
    }

    val m = msg
    if (m != null) {
        AlertDialog(onDismissRequest = { msg = null }, confirmButton = { TextButton(onClick = { msg = null }) { Text("حسناً") } }, text = { Text(m) })
    }
}

@Composable
private fun ExportPassDialog(onOk: (String) -> Unit, onCancel: () -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("كلمة سر النسخة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = p1, onValueChange = { p1 = it }, singleLine = true, label = { Text("كلمة السر (4 أحرف فأكثر)") }, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(value = p2, onValueChange = { p2 = it }, singleLine = true, label = { Text("أعد كتابتها") }, visualTransformation = PasswordVisualTransformation())
                Text("احفظها جيداً: لا يمكن استعادة النسخة بدونها.", fontSize = 11.sp)
                if (err != null) Text(err ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (p1.length < 4) {
                    err = "كلمة السر قصيرة"
                } else if (p1 != p2) {
                    err = "كلمتا السر غير متطابقتين"
                } else {
                    onOk(p1)
                }
            }) { Text("متابعة") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } }
    )
}

private fun latinDigits(s: String): String {
    val sb = StringBuilder()
    for (ch in s.trim()) {
        sb.append(if (ch in '٠'..'٩') ('0' + (ch - '٠')) else ch)
    }
    return sb.toString()
}

@Composable
private fun ChallengeDialog(onOk: () -> Unit, onCancel: () -> Unit) {
    val a = remember { 6 + (Math.random() * 9).toInt() }
    val b = remember { 3 + (Math.random() * 7).toInt() }
    var ans by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("تأكيد نهائي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("لتأكيد الحذف النهائي: ما ناتج $a × $b ؟")
                OutlinedTextField(
                    value = ans, onValueChange = { ans = it }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (err != null) Text(err ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (latinDigits(ans) == (a * b).toString()) {
                    onOk()
                } else {
                    err = "إجابة غير صحيحة — لم يُحذف شيء"
                }
            }) { Text("حذف نهائي", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } }
    )
}
