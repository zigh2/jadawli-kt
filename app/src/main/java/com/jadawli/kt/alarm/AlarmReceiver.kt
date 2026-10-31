package com.jadawli.kt.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmEngine.handle(context, intent)
    }
}

/** يعيد جدولة المنبهات بعد إعادة تشغيل الهاتف أو تحديث التطبيق. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmEngine.scheduleAll(context)
    }
}
