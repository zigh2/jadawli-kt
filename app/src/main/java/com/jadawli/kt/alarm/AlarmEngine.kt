package com.jadawli.kt.alarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.PowerManager
import com.jadawli.kt.MainActivity
import com.jadawli.kt.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** محرك المنبهات: يجدول بنظام المنبهات في أندرويد ولا يبقي التطبيق يعمل في الخلفية. منقول من جداولي القديم. */
object AlarmEngine {
    const val PREFS = "jd_alarms"
    const val CH_PRE = "jd_pre"
    const val CH_ALERT = "jd_alert"
    const val CH_RING = "jd_ring"
    const val ACT_FIRE = "com.jadawli.kt.ALARM_FIRE"
    const val ACT_STOP = "com.jadawli.kt.ALARM_STOP"
    const val TEST_ID = "__test__"
    const val RING_SECS = 60
    const val SNOOZE_DEFAULT = 10

    /** سجل قصير لآخر الأحداث (للتشخيص). */
    fun log(c: Context, m: String) {
        try {
            val p = sp(c)
            val line = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date()) + "  " + m + "\n"
            var all = line + p.getString("log", "")
            if (all.length > 6000) all = all.substring(0, 6000)
            p.edit().putString("log", all).apply()
        } catch (e: Exception) { /* لا شيء */ }
    }

    fun readLog(c: Context): String = sp(c).getString("log", "") ?: ""

    fun sp(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notifId(id: String): Int = 1000 + (Math.abs(id.hashCode()) % 90000)

    fun saveConfig(c: Context, json: String) { sp(c).edit().putString("cfg", json).apply() }

    /** يقرأ الإعدادات المحفوظة مع تواريخ التسجيل/الإيقاف لكل منبه. */
    fun load(c: Context): List<Sched.Cfg> {
        val out = ArrayList<Sched.Cfg>()
        val p = sp(c)
        try {
            val arr = JSONArray(p.getString("cfg", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val a = Sched.Cfg()
                a.id = o.optString("id")
                a.title = o.optString("title")
                a.label = o.optString("label")
                val hm = o.optString("time", "07:00").split(":")
                a.hour = hm[0].trim().toInt()
                a.minute = hm[1].trim().toInt()
                a.days = o.optInt("days", 0)
                a.pre = maxOf(0, o.optInt("pre", 0))
                a.post = maxOf(0, o.optInt("post", 0))
                a.rep = maxOf(0, o.optInt("rep", 0))
                a.cnt = maxOf(0, o.optInt("cnt", 0))
                a.on = o.optBoolean("on", true)
                a.full = o.optBoolean("full", true)
                a.done = p.getString("done:" + a.id, "") ?: ""
                a.stop = p.getString("stop:" + a.id, "") ?: ""
                if (a.id.isNotEmpty()) out.add(a)
            }
        } catch (e: Exception) { /* إعدادات تالفة: لا شيء يُجدول */ }
        return out
    }

    fun find(c: Context, id: String): Sched.Cfg? = load(c).firstOrNull { it.id == id }

    private fun am(c: Context): AlarmManager = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    fun nm(c: Context): NotificationManager = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private fun req(id: String): Int = Math.abs(id.hashCode()) % 100000

    private fun firePi(c: Context, a: Sched.Cfg, e: Sched.Ev?): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(ACT_FIRE).putExtra("id", a.id)
        if (e != null) i.putExtra("type", e.type).putExtra("idx", e.idx).putExtra("date", e.date).putExtra("main", e.main).putExtra("at", e.at)
        return PendingIntent.getBroadcast(c, req(a.id), i, flags())
    }

    fun openPi(c: Context, id: String?, date: String?): PendingIntent {
        val i = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (id != null) i.putExtra("alarm_id", id).putExtra("alarm_date", date)
        return PendingIntent.getActivity(c, req(id ?: "open") + 100000, i, flags())
    }

    /** يجدول الحدث التالي لمنبه واحد (أو يلغي جدولته). */
    fun scheduleOne(c: Context, a: Sched.Cfg, after: Long) {
        try {
            val e = Sched.next(a, after, TimeZone.getDefault())
            if (e == null) { am(c).cancel(firePi(c, a, null)); return }
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(e.at, openPi(c, null, null)), firePi(c, a, e))
        } catch (ex: Exception) { /* لا نكسر التطبيق إن رفض النظام الجدولة */ }
    }

    /** يجدول كل المنبهات ويستعيد إشعارات النوافذ الجارية (بعد إعادة التشغيل أو تعديل الإعدادات). */
    fun scheduleAll(c: Context) {
        val now = System.currentTimeMillis()
        rearmSnoozes(c, null)
        for (a in load(c)) {
            scheduleOne(c, a, now)
            if (AlarmService.running) continue           // لا نقطع رنيناً جارياً
            if (snoozed(c, a.id)) continue               // مؤجَّل: يبقى إشعار التأجيل كما هو
            val e = Sched.active(a, now, TimeZone.getDefault())
            if (e == null) nm(c).cancel(notifId(a.id))
            else if (e.type == Sched.PRE) showPre(c, a, e)
            else showAlert(c, a, e, true)
        }
    }

    /* ---------- التأجيل («لاحقاً») ---------- */

    private fun snoozePi(c: Context, id: String, at: Long, date: String, main: Long): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(ACT_FIRE).putExtra("id", id).putExtra("type", Sched.SNOOZE)
            .putExtra("date", date).putExtra("main", main).putExtra("at", at)
        return PendingIntent.getBroadcast(c, req(id) + 300000, i, flags())
    }

    private fun armSnooze(c: Context, id: String, date: String, at: Long, main: Long) {
        try {
            am(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, openPi(c, null, null)), snoozePi(c, id, at, date, main))
        } catch (ex: Exception) { log(c, "SNOOZE arm failed: $ex") }
    }

    private fun cancelSnooze(c: Context, id: String) {
        sp(c).edit().remove("snz:$id").apply()
        try { am(c).cancel(snoozePi(c, id, 0, "", 0)) } catch (e: Exception) { /* لا شيء */ }
    }

    /** موعد الحدث الأصلي (لحساب عدّاد + بعد التأجيل). */
    private fun mainOf(c: Context, id: String, date: String, fallback: Long): Long {
        val a = find(c, id)
        if (a == null || !Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) return fallback
        val p = date.split("-")
        val m = Calendar.getInstance()
        m.clear()
        m.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), a.hour, a.minute, 0)
        return m.timeInMillis
    }

    /** يؤجّل المنبّه: يوقف الرنين ويعيده بعد عدد من الدقائق، ويُبقي إشعاراً بعدّاد تنازلي. */
    fun snooze(c: Context, id: String, date: String?, minutes: Int) {
        val m = maxOf(1, minOf(120, if (minutes <= 0) SNOOZE_DEFAULT else minutes))
        val at = System.currentTimeMillis() + m * 60000L
        val d = date ?: ""
        val main = mainOf(c, id, d, at)
        sp(c).edit().putString("snz:$id", "$d|$at|$main").apply()
        stopRing(c)
        armSnooze(c, id, d, at, main)
        showSnoozed(c, id, d, at)
        log(c, "SNOOZE $id ${m}min")
    }

    private fun showSnoozed(c: Context, id: String, date: String, at: Long) {
        ensureChannels(c)
        val a = find(c, id)
        val title = if (a == null) (if (TEST_ID == id) "تجربة المنبّه" else "المنبّه") else titleOf(a)
        val st = Intent(c, AlarmReceiver::class.java).setAction(ACT_STOP).putExtra("id", id).putExtra("date", date)
        val stop = PendingIntent.getBroadcast(c, req(id) + 200000, st, flags())
        val whenTxt = SimpleDateFormat("HH:mm", Locale.US).format(Date(at))
        val b = Notification.Builder(c, CH_PRE).setSmallIcon(R.drawable.ic_stat_alarm).setContentTitle(title)
            .setContentText("مؤجَّل — يرنّ مجدداً $whenTxt")
            .setWhen(at).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(false).setAutoCancel(false).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion(c, CH_PRE))
            .setContentIntent(openPi(c, id, date)).addAction(0, "إيقاف", stop)
        nm(c).notify(notifId(id), b.build())
    }

    /** يعيد جدولة التأجيلات المعلّقة (بعد إعادة التشغيل أو تعديل الإعدادات). */
    private fun rearmSnoozes(c: Context, onlyId: String?) {
        val now = System.currentTimeMillis()
        val p = sp(c)
        for ((k, v) in HashMap(p.all)) {
            if (!k.startsWith("snz:") || v !is String) continue
            val id = k.substring(4)
            if (onlyId != null && onlyId != id) continue
            val f = v.split("|")
            try {
                val at = f[1].toLong()
                val main = if (f.size > 2) f[2].toLong() else at
                if (at > now) armSnooze(c, id, f[0], at, main) else p.edit().remove(k).apply()
            } catch (e: Exception) { p.edit().remove(k).apply() }
        }
    }

    private fun snoozed(c: Context, id: String): Boolean {
        val v = sp(c).getString("snz:$id", "") ?: ""
        if (v.isEmpty()) return false
        return try { v.split("|")[1].toLong() > System.currentTimeMillis() } catch (e: Exception) { false }
    }

    fun setDone(c: Context, id: String, date: String, done: Boolean) {
        val ed = sp(c).edit()
        if (done) ed.putString("done:$id", date)
        else if (date == sp(c).getString("done:$id", "")) ed.remove("done:$id")
        ed.apply()
        if (done) { cancelSnooze(c, id); stopRing(c); nm(c).cancel(notifId(id)); log(c, "DONE $id $date") }
        scheduleAll(c)
    }

    /** إيقاف تكرار منبه ليوم معيّن وإزالة إشعاره. */
    fun stopToday(c: Context, id: String, date: String) {
        sp(c).edit().putString("stop:$id", date).apply()
        cancelSnooze(c, id)
        stopRing(c)
        nm(c).cancel(notifId(id))
        log(c, "STOP $id $date")
        val s = find(c, id)
        if (s != null) scheduleOne(c, s, System.currentTimeMillis())
    }

    /** تنبيه تجريبي بعد عدد من الثواني، للتأكد أن الإشعار والصوت وملء الشاشة تعمل على هذا الهاتف. */
    fun test(c: Context, secs: Int) {
        val i = Intent(c, AlarmReceiver::class.java).setAction(ACT_FIRE).putExtra("id", TEST_ID).putExtra("type", Sched.MAIN)
        val pi = PendingIntent.getBroadcast(c, 424242, i, flags())
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + secs * 1000L, openPi(c, null, null)), pi)
    }

    fun handle(c: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val date = intent.getStringExtra("date")
        log(c, "FIRE id=$id type=${intent.getIntExtra("type", -1)} date=$date")
        if (ACT_STOP == intent.action) { stopToday(c, id, date ?: ""); return }
        if (intent.getIntExtra("type", -1) == Sched.SNOOZE) sp(c).edit().remove("snz:$id").apply()
        if (TEST_ID == id) {
            val t = Sched.Cfg()
            t.id = id
            t.title = "تجربة المنبّه"
            val e = Sched.Ev()
            e.type = Sched.MAIN
            e.date = Sched.ymd(Calendar.getInstance())
            e.main = System.currentTimeMillis()
            e.at = e.main
            ring(c, t, e)
            return
        }
        val a = find(c, id)
        if (a == null || !a.on) return
        val type = intent.getIntExtra("type", Sched.MAIN)
        val e = Sched.Ev()
        e.type = type
        e.idx = intent.getIntExtra("idx", 0)
        e.date = date ?: ""
        e.main = intent.getLongExtra("main", System.currentTimeMillis())
        e.at = intent.getLongExtra("at", e.main)
        if (e.date == a.done) { stopRing(c); nm(c).cancel(notifId(id)); log(c, "SKIP done $id") }
        else if (type == Sched.PRE) { stopRing(c); showPre(c, a, e) }
        else if (type == Sched.END) { stopRing(c); nm(c).cancel(notifId(id)) }
        else ring(c, a, e)
        scheduleOne(c, a, maxOf(System.currentTimeMillis(), e.at))
    }

    /* ---------- الإشعارات ---------- */

    fun ensureChannels(c: Context) {
        val pre = NotificationChannel(CH_PRE, "العدّ التنازلي للمنبّه", NotificationManager.IMPORTANCE_LOW)
        pre.setSound(null, null)
        pre.setShowBadge(false)
        val al = NotificationChannel(CH_ALERT, "تنبيهات المنبّه", NotificationManager.IMPORTANCE_HIGH)
        val aa = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        al.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), aa)
        al.enableVibration(true)
        al.vibrationPattern = longArrayOf(0, 500, 300, 500)
        val ring = NotificationChannel(CH_RING, "رنين المنبّه", NotificationManager.IMPORTANCE_HIGH)
        ring.setSound(null, null)                    // الصوت تتولاه الخدمة (حلقة مستمرة)
        ring.enableVibration(false)
        nm(c).createNotificationChannel(pre)
        nm(c).createNotificationChannel(al)
        nm(c).createNotificationChannel(ring)
    }

    private fun hhmm(a: Sched.Cfg): String = String.format(Locale.US, "%02d:%02d", a.hour, a.minute)

    private fun titleOf(a: Sched.Cfg): String {
        val t = if (a.title.isNotEmpty()) a.title else "المنبّه"
        return if (a.label.isNotEmpty()) "$t — ${a.label}" else t
    }

    /** نسخة عامة تظهر على شاشة القفل: لا تكشف اسم الجدول ولا العمود. */
    private fun publicVersion(c: Context, ch: String): Notification =
        Notification.Builder(c, ch).setSmallIcon(R.drawable.ic_stat_alarm).setContentTitle("المنبّه").build()

    private fun showPre(c: Context, a: Sched.Cfg, e: Sched.Ev) {
        ensureChannels(c)
        val b = Notification.Builder(c, CH_PRE).setSmallIcon(R.drawable.ic_stat_alarm).setContentTitle(titleOf(a))
            .setContentText("متبقٍ على الموعد " + hhmm(a))
            .setWhen(e.main).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion(c, CH_PRE))
            .setContentIntent(openPi(c, a.id, e.date))
        nm(c).notify(notifId(a.id), b.build())
    }

    /** mode: 0 هادئ، 1 يرنّ عبر الخدمة (قناة صامتة)، 2 صوت إشعار عادي (احتياط). */
    fun buildAlert(c: Context, a: Sched.Cfg, e: Sched.Ev, mode: Int): Notification {
        ensureChannels(c)
        val ch = if (mode == 0) CH_PRE else if (mode == 1) CH_RING else CH_ALERT
        val windowed = a.pre > 0 || a.post > 0
        val open = openPi(c, a.id, e.date)
        val st = Intent(c, AlarmReceiver::class.java).setAction(ACT_STOP).putExtra("id", a.id).putExtra("date", e.date)
        val stop = PendingIntent.getBroadcast(c, req(a.id) + 200000, st, flags())
        val b = Notification.Builder(c, ch).setSmallIcon(R.drawable.ic_stat_alarm).setContentTitle(titleOf(a))
            .setContentText(if (windowed) "تجاوز الموعد " + hhmm(a) + " ولم تُسجَّل القيمة  (+)" else "حان موعد المنبّه " + hhmm(a))
            .setWhen(e.main).setShowWhen(true).setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion(c, ch))
            .setContentIntent(open).setOngoing(windowed || mode == 1).setAutoCancel(!windowed && mode != 1)
            .addAction(0, "إيقاف", stop)
        if (windowed) b.setUsesChronometer(true).setChronometerCountDown(false)
        if (a.full && mode != 0) b.setFullScreenIntent(open, true)
        return b.build()
    }

    private fun showAlert(c: Context, a: Sched.Cfg, e: Sched.Ev, quiet: Boolean) {
        nm(c).notify(notifId(a.id), buildAlert(c, a, e, if (quiet) 0 else 2))
    }

    /** يوقظ الشاشة عند الرنين (مستقلاً عن إذن الإشعار بملء الشاشة). */
    @Suppress("DEPRECATION")
    private fun wake(c: Context) {
        try {
            val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "jadawli:alarm"
            )
            wl.acquire(15000)
            log(c, "WAKE screen")
        } catch (t: Throwable) { log(c, "WAKE failed: $t") }
    }

    /** يرنّ كالساعة: خدمة تشغّل نغمة المنبّه في حلقة (حتى يوقفها المستخدم أو تنتهي المدة). وعند فشلها نعود لصوت الإشعار. */
    private fun ring(c: Context, a: Sched.Cfg, e: Sched.Ev) {
        wake(c)
        val i = Intent(c, AlarmService::class.java).putExtra("id", a.id).putExtra("title", a.title).putExtra("label", a.label)
            .putExtra("pre", a.pre).putExtra("post", a.post).putExtra("full", a.full)
            .putExtra("date", e.date).putExtra("main", e.main).putExtra("type", e.type).putExtra("idx", e.idx).putExtra("secs", RING_SECS)
        try {
            val cn = c.startForegroundService(i)
            if (cn == null) throw IllegalStateException("خدمة الرنين غير معرّفة في المانيفست")
            log(c, "RING start ${a.id} type=${e.type} idx=${e.idx}")
        } catch (ex: Exception) {
            log(c, "RING service failed: $ex")
            try { nm(c).notify(notifId(a.id), buildAlert(c, a, e, 2)) } catch (x: Exception) { log(c, "NOTIFY failed: $x") }
        }
    }

    /** يكتم الصوت ويُبقي إشعار الرنين ظاهراً (تفصله الخدمة عن نفسها قبل أن تتوقف). */
    fun silenceRing(c: Context) {
        if (!AlarmService.running) return
        try { c.startService(Intent(c, AlarmService::class.java).putExtra("silence", true)) }
        catch (e: Exception) { stopRing(c) }
    }

    fun stopRing(c: Context) {
        try { c.stopService(Intent(c, AlarmService::class.java)) } catch (e: Exception) { /* لا شيء */ }
    }
}
