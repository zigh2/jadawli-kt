package com.jadawli.kt

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.jadawli.kt.alarm.AlarmEngine
import com.jadawli.kt.alarm.AlarmLaunch
import com.jadawli.kt.alarm.AlarmStore
import com.jadawli.kt.data.AppDb
import com.jadawli.kt.data.AppScope
import com.jadawli.kt.data.AutoBackup
import com.jadawli.kt.security.Sec
import com.jadawli.kt.ui.GateHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()
    private var leftAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent?.hasExtra("alarm_id") == true) overLock(true)   // قبل ظهور النافذة كي يظهر فوق القفل
        super.onCreate(savedInstanceState)
        val sec = Sec.get(this)
        handleAlarmIntent(intent)

        lifecycleScope.launch {
            sec.secure.collect { s ->
                if (s) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        lifecycleScope.launch {
            AlarmLaunch.pending.collect { p -> if (p == null) overLock(false) }
        }
        // إعادة جدولة المنبهات عند كل فتح (تعديلات الإعدادات، تغيّر اليوم...)
        val app = applicationContext
        lifecycleScope.launch(Dispatchers.Default) {
            AlarmStore.ensure(app)
            AlarmEngine.saveConfig(app, AlarmStore.nativeJson(AlarmStore.list.value))
            AlarmEngine.scheduleAll(app)
        }

        setContent {
            val theme by sec.theme.collectAsStateWithLifecycle()
            val dark = when (theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    GateHost(sec, vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlarmIntent(intent)
    }

    /** نية تنبيه المنبّه (من الإشعار أو ملء الشاشة): تُعرض شاشة التنبيه فوق القفل. */
    private fun handleAlarmIntent(i: Intent?) {
        if (i != null && i.hasExtra("alarm_id")) {
            val id = i.getStringExtra("alarm_id") ?: return
            val date = i.getStringExtra("alarm_date") ?: LocalDate.now().toString()
            i.removeExtra("alarm_id")
            AlarmLaunch.pending.value = Pair(id, date.ifEmpty { LocalDate.now().toString() })
            overLock(true)
        }
    }

    /** يسمح بعرض شاشة التنبيه فوق شاشة القفل وتشغيل الشاشة، ويُلغى عند الانتهاء. */
    private fun overLock(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        } else {
            @Suppress("DEPRECATION")
            val f = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (on) window.addFlags(f) else window.clearFlags(f)
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = SystemClock.elapsedRealtime()
        // نسخ احتياطي تلقائي: مرة يومياً عند الخروج، وفقط إن تغيّرت البيانات
        val app = applicationContext
        if (AutoBackup.isOn(app)) {
            AppScope.io.launch { AutoBackup.run(app, AppDb.get(app), true) }
        }
    }

    override fun onStart() {
        super.onStart()
        // يُقفل التطبيق إن غاب أكثر من 20 ثانية (يتسع لاختيار ملف أو حوار البصمة)
        if (leftAt != 0L && SystemClock.elapsedRealtime() - leftAt > 20_000L) Sec.get(this).lockIfNeeded()
    }
}
