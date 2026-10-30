package com.jadawli.kt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadawli.kt.MainViewModel
import com.jadawli.kt.security.Bio
import com.jadawli.kt.security.Sec
import com.jadawli.kt.security.Verify
import com.jadawli.kt.security.findActivity
import kotlinx.coroutines.delay
import java.time.LocalTime

/** يعرض التطبيق أو شاشة القفل/التمويه بحسب الحالة. */
@Composable
fun GateHost(sec: Sec, vm: MainViewModel) {
    val locked by sec.locked.collectAsStateWithLifecycle()
    if (!locked) {
        JadawliApp(vm, sec)
    } else {
        UnlockHost(sec)
    }
}

@Composable
private fun UnlockHost(sec: Sec) {
    val ctx = LocalContext.current
    val lockOn by sec.lockOn.collectAsStateWithLifecycle()
    val bioOn by sec.bioOn.collectAsStateWithLifecycle()
    val disguise by sec.disguiseScreen.collectAsStateWithLifecycle()
    var pinDlg by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }

    fun tryBio() {
        val act = ctx.findActivity()
        if (act != null && Bio.can(ctx)) {
            Bio.prompt(act, onOk = { sec.unlock() }, onFail = { if (disguise) pinDlg = true })
        } else if (disguise) {
            pinDlg = true
        }
    }
    fun begin() {
        if (!lockOn) {
            sec.unlock()
        } else if (bioOn) {
            tryBio()
        } else {
            pinDlg = true
        }
    }
    fun checkPin(pin: String) {
        when (val r = sec.verify(pin)) {
            is Verify.Ok -> { sec.unlock() }
            is Verify.Bad -> { err = "رمز غير صحيح — بقي ${r.left} محاولات" }
            is Verify.Locked -> { err = "حاول بعد ${r.sec} ثانية" }
        }
    }

    if (disguise) {
        DisguiseScreen(onUnlock = { begin() })
        if (pinDlg) {
            PinDialog(sec, "أدخل الرمز", onOk = { pinDlg = false; sec.unlock() }, onCancel = { pinDlg = false })
        }
    } else {
        LaunchedEffect(Unit) { if (bioOn) tryBio() }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔒", fontSize = 42.sp)
            Text("جداولي", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            PinPad("أدخل الرمز", err) { pin -> checkPin(pin) }
            if (bioOn) {
                TextButton(onClick = { tryBio() }, modifier = Modifier.padding(top = 8.dp)) { Text("🖐️ فتح بالبصمة", fontSize = 16.sp) }
            }
        }
    }
}

/* ---------------- لوحة الأرقام ---------------- */

@Composable
fun PinPad(title: String, error: String?, onSubmit: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val prim = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outline
    val keyBg = MaterialTheme.colorScheme.surfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            repeat(maxOf(pin.length, 4)) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) prim else off))
            }
        }
        Text(error ?: " ", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓")).forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        r.forEach { k ->
                            Box(
                                Modifier.size(62.dp).clip(CircleShape).background(keyBg).clickable {
                                    if (k == "⌫") {
                                        pin = pin.dropLast(1)
                                    } else if (k == "✓") {
                                        if (pin.length >= 4) {
                                            val p = pin
                                            pin = ""
                                            onSubmit(p)
                                        }
                                    } else if (pin.length < 8) {
                                        pin += k
                                    }
                                },
                                contentAlignment = Alignment.Center
                            ) { Text(k, fontSize = 22.sp) }
                        }
                    }
                }
            }
        }
    }
}

/** حوار يتحقق من الرمز الحالي. */
@Composable
fun PinDialog(sec: Sec, title: String, onOk: () -> Unit, onCancel: () -> Unit) {
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PinPad(title, err) { pin ->
                    when (val r = sec.verify(pin)) {
                        is Verify.Ok -> { onOk() }
                        is Verify.Bad -> { err = "رمز غير صحيح — بقي ${r.left} محاولات" }
                        is Verify.Locked -> { err = "حاول بعد ${r.sec} ثانية" }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } }
    )
}

/** اختيار رمز جديد بخطوتين (إدخال ثم تأكيد). */
@Composable
fun PinSetDialog(onDone: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCancel,
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PinPad(
                    if (first == null) "اختر رمزاً من 4 أرقام أو أكثر ثم اضغط ✓" else "أعد إدخال الرمز للتأكيد",
                    err
                ) { pin ->
                    val f = first
                    if (f == null) {
                        first = pin
                        err = null
                    } else if (f == pin) {
                        onDone(pin)
                    } else {
                        first = null
                        err = "الرمزان غير متطابقين، ابدأ من جديد"
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancel) { Text("إلغاء") } }
    )
}

/* ---------------- شاشة التمويه (تبدو كتطبيق منبّه) ---------------- */

private class FakeAlarm(val time: String, val label: String, on: Boolean) {
    var on by mutableStateOf(on)
}

@Composable
fun DisguiseScreen(onUnlock: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }
    val alarms = remember {
        mutableStateListOf(FakeAlarm("05:00", "الاستيقاظ", true), FakeAlarm("06:30", "العمل", false), FakeAlarm("07:15", "تذكير", false))
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // الضغط المطول على أيقونة الساعة يفتح التطبيق
            Box(
                Modifier.size(44.dp).pointerInput(Unit) { detectTapGestures(onLongPress = { onUnlock() }) },
                contentAlignment = Alignment.Center
            ) { Text("⏰", fontSize = 24.sp) }
            Text("المنبّه", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Button(onClick = { alarms.add(FakeAlarm("07:00", "منبّه", false)) }) { Text("＋ منبّه") }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text("%02d:%02d".format(now.hour, now.minute), fontSize = 64.sp, fontWeight = FontWeight.Light)
        }
        alarms.forEach { a ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.time, fontSize = 28.sp)
                    Text(a.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = a.on, onCheckedChange = { a.on = it })
            }
        }
    }
}
