package com.jadawli.kt.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

sealed interface Verify {
    object Ok : Verify
    data class Bad(val left: Int) : Verify
    data class Locked(val sec: Long) : Verify
}

/** إعدادات الأمان والمظهر + حالة القفل. الرمز يُخزَّن مملّحاً ومجزّأً (SHA-256) لا نصاً. */
class Sec private constructor(private val app: Context) {
    private val p = app.getSharedPreferences("security", Context.MODE_PRIVATE)

    val lockOn = MutableStateFlow(p.getBoolean("lockOn", false))
    val bioOn = MutableStateFlow(p.getBoolean("bioOn", false))
    val disguiseScreen = MutableStateFlow(p.getBoolean("disguiseScreen", false))
    val disguiseIcon = MutableStateFlow(p.getBoolean("disguiseIcon", false))
    val secure = MutableStateFlow(p.getBoolean("secure", false))
    val theme = MutableStateFlow(p.getString("theme", "auto") ?: "auto")
    val defaultView = MutableStateFlow(p.getString("defaultView", "auto") ?: "auto")
    val locked = MutableStateFlow(lockOn.value || disguiseScreen.value)

    private fun putB(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply() }

    fun setLockOn(v: Boolean) { lockOn.value = v; putB("lockOn", v); if (!v) setBioOn(false) }
    fun setBioOn(v: Boolean) { bioOn.value = v; putB("bioOn", v) }
    fun setDisguiseScreen(v: Boolean) { disguiseScreen.value = v; putB("disguiseScreen", v) }
    fun setSecure(v: Boolean) { secure.value = v; putB("secure", v) }
    fun setTheme(v: String) { theme.value = v; p.edit().putString("theme", v).apply() }
    fun setDefaultView(v: String) { defaultView.value = v; p.edit().putString("defaultView", v).apply() }

    /** يبدّل أيقونة التطبيق واسمه بين «جداولي» و«المنبّه». */
    fun setIconDisguise(on: Boolean): Boolean {
        return try {
            val pm = app.packageManager
            val normal = ComponentName(app.packageName, "com.jadawli.kt.LauncherNormal")
            val alarm = ComponentName(app.packageName, "com.jadawli.kt.LauncherAlarm")
            pm.setComponentEnabledSetting(if (on) alarm else normal, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(if (on) normal else alarm, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            disguiseIcon.value = on
            putB("disguiseIcon", on)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun needsGate(): Boolean = lockOn.value || disguiseScreen.value
    fun lockIfNeeded() { if (needsGate()) locked.value = true }
    fun unlock() { locked.value = false }

    /* ---- الرمز ---- */

    fun hasPin(): Boolean = p.contains("ph")

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    private fun hash(salt: String, pin: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray(Charsets.UTF_8)))

    fun setPin(pin: String) {
        val b = ByteArray(12)
        SecureRandom().nextBytes(b)
        val salt = hex(b)
        p.edit().putString("ps", salt).putString("ph", hash(salt, pin)).putInt("fails", 0).putLong("until", 0L).apply()
    }

    fun removePin() {
        p.edit().remove("ps").remove("ph").apply()
        setLockOn(false)
    }

    /** 5 محاولات خاطئة تُوقف المحاولة 30 ثانية. */
    fun verify(pin: String): Verify {
        val now = System.currentTimeMillis()
        val until = p.getLong("until", 0L)
        if (now < until) return Verify.Locked((until - now) / 1000 + 1)
        val salt = p.getString("ps", null)
        val h = p.getString("ph", null)
        if (salt != null && h != null && hash(salt, pin) == h) {
            p.edit().putInt("fails", 0).apply()
            return Verify.Ok
        }
        val f = p.getInt("fails", 0) + 1
        if (f >= 5) {
            p.edit().putInt("fails", 0).putLong("until", now + 30_000L).apply()
            return Verify.Locked(30)
        }
        p.edit().putInt("fails", f).apply()
        return Verify.Bad(5 - f)
    }

    /** الإعدادات التي تدخل في النسخة الاحتياطية (بنفس أسماء جداولي). */
    fun settingsJson(): JSONObject {
        val o = JSONObject()
        o.put("theme", theme.value)
        if (defaultView.value != "auto") o.put("view", defaultView.value)
        return o
    }

    companion object {
        @Volatile private var inst: Sec? = null
        fun get(ctx: Context): Sec = inst ?: synchronized(this) {
            inst ?: Sec(ctx.applicationContext).also { inst = it }
        }
    }
}
