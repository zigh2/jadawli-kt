package com.jadawli.kt.alarm

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** منطق جدولة المنبهات فقط (بلا اعتماد على أندرويد)؛ منقول من محرك جداولي القديم كما هو. */
object Sched {
    const val PRE = 0
    const val MAIN = 1
    const val REP = 2
    const val END = 3
    const val SNOOZE = 4

    class Cfg {
        var id = ""
        var title = ""
        var label = ""
        var hour = 0
        var minute = 0
        var days = 0
        var pre = 0
        var post = 0
        var rep = 0
        var cnt = 0
        var on = true
        var full = true
        var done = ""
        var stop = ""
    }

    class Ev(var at: Long = 0, var main: Long = 0, var type: Int = 0, var idx: Int = 0, var date: String = "")

    fun ymd(c: Calendar): String =
        String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    /** أحداث منبه واحد في يوم واحد (d = بداية اليوم). تُهمل الأيام غير المفعّلة. */
    fun gen(a: Cfg, d: Calendar, out: MutableList<Ev>) {
        val dow = d.get(Calendar.DAY_OF_WEEK) - 1            // 0 = الأحد
        if (a.days != 0 && (a.days and (1 shl dow)) == 0) return
        val ds = ymd(d)
        val m = d.clone() as Calendar
        m.set(Calendar.HOUR_OF_DAY, a.hour)
        m.set(Calendar.MINUTE, a.minute)
        val main = m.timeInMillis
        if (a.pre > 0) out.add(Ev(main - a.pre * 60000L, main, PRE, 0, ds))
        out.add(Ev(main, main, MAIN, 0, ds))
        if (a.rep > 0) for (i in 1..a.cnt) out.add(Ev(main + i.toLong() * a.rep * 60000L, main, REP, i, ds))
        if (a.pre > 0 || a.post > 0 || (a.rep > 0 && a.cnt > 0)) {
            val endMin = maxOf(a.post, a.cnt * a.rep + 1)     // النهاية بعد آخر تكرار دائماً
            out.add(Ev(main + endMin * 60000L, main, END, 0, ds))
        }
    }

    private fun dayStart(t: Long, tz: TimeZone, offset: Int): Calendar {
        val d = Calendar.getInstance(tz)
        d.timeInMillis = t
        d.set(Calendar.HOUR_OF_DAY, 0)
        d.set(Calendar.MINUTE, 0)
        d.set(Calendar.SECOND, 0)
        d.set(Calendar.MILLISECOND, 0)
        d.add(Calendar.DAY_OF_MONTH, offset)
        return d
    }

    /** أقرب حدث بعد after. يُتخطى كل ما يخص يوماً تم تسجيله، ويُتخطى التكرار في يوم أُوقف. */
    fun next(a: Cfg, after: Long, tz: TimeZone): Ev? {
        if (!a.on) return null
        var best: Ev? = null
        val d = dayStart(after, tz, -1)
        for (off in -1..8) {
            val list = ArrayList<Ev>()
            gen(a, d, list)
            for (e in list) {
                if (e.date == a.done) continue
                if (e.type == REP && e.date == a.stop) continue
                if (e.at > after && (best == null || e.at < best.at)) best = e
            }
            d.add(Calendar.DAY_OF_MONTH, 1)
        }
        return best
    }

    /** الحدث الحالي إن كان الوقت داخل نافذة منبه لم يُسجَّل (لاستعادة الإشعار بعد إعادة التشغيل). */
    fun active(a: Cfg, now: Long, tz: TimeZone): Ev? {
        if (!a.on) return null
        var best: Ev? = null
        val d = dayStart(now, tz, -1)
        for (off in -1..0) {
            val list = ArrayList<Ev>()
            gen(a, d, list)
            for (e in list) {
                if (e.date == a.done || e.date == a.stop) continue
                if (e.at <= now && (best == null || e.at > best.at)) best = e
            }
            d.add(Calendar.DAY_OF_MONTH, 1)
        }
        if (best == null || best.type == END) return null
        val windowed = a.pre > 0 || a.post > 0 || (a.rep > 0 && a.cnt > 0)
        if (!windowed && now - best.at > 3600000L) return null   // منبه عادي: يُستعاد ساعة واحدة فقط
        return best
    }
}
