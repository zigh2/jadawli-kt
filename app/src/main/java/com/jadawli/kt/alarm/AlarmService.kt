package com.jadawli.kt.alarm

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** خدمة الرنين: تشغّل نغمة المنبّه في حلقة مع اهتزاز، وتنتهي بعد مدة أو عند الإيقاف. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var vib: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable {
        AlarmEngine.log(this, "RING timeout")
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inp = intent
        if (inp == null) { stopSelf(); return START_NOT_STICKY }
        if (inp.getBooleanExtra("silence", false)) {          // كتم: نفصل الإشعار قبل التوقف كي لا يُحذف مع الخدمة
            handler.removeCallbacks(stopper)
            stopSound()
            try { stopForeground(STOP_FOREGROUND_DETACH) } catch (e: Exception) { /* لا شيء */ }
            running = false
            stopSelf()
            return START_NOT_STICKY
        }
        val a = Sched.Cfg()
        a.id = inp.getStringExtra("id") ?: ""
        a.title = inp.getStringExtra("title") ?: ""
        a.label = inp.getStringExtra("label") ?: ""
        a.pre = inp.getIntExtra("pre", 0)
        a.post = inp.getIntExtra("post", 0)
        a.full = inp.getBooleanExtra("full", true)
        val e = Sched.Ev()
        e.date = inp.getStringExtra("date") ?: ""
        e.main = inp.getLongExtra("main", System.currentTimeMillis())
        e.type = inp.getIntExtra("type", Sched.MAIN)
        e.idx = inp.getIntExtra("idx", 0)
        val secs = maxOf(5, inp.getIntExtra("secs", AlarmEngine.RING_SECS))
        try {
            val n: Notification = AlarmEngine.buildAlert(this, a, e, 1)
            val nid = AlarmEngine.notifId(a.id)
            if (Build.VERSION.SDK_INT >= 29) startForeground(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(nid, n)
            running = true
        } catch (ex: Exception) {
            AlarmEngine.log(this, "startForeground failed: $ex")
            try { AlarmEngine.nm(this).notify(AlarmEngine.notifId(a.id), AlarmEngine.buildAlert(this, a, e, 2)) } catch (x: Exception) { /* لا شيء */ }
            stopSelf()
            return START_NOT_STICKY
        }
        startSound()
        handler.removeCallbacks(stopper)
        handler.postDelayed(stopper, secs * 1000L)
        return START_NOT_STICKY
    }

    private fun startSound() {
        stopSound()
        try {
            var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
            AlarmEngine.log(this, "SOUND started")
        } catch (ex: Exception) {
            player = null
            AlarmEngine.log(this, "SOUND failed: $ex")
        }
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator?
            vib = v
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        } catch (ex: Exception) { /* الاهتزاز اختياري */ }
    }

    private fun stopSound() {
        try { player?.stop(); player?.release() } catch (e: Exception) { /* لا شيء */ }
        player = null
        try { vib?.cancel() } catch (e: Exception) { /* لا شيء */ }
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopper)
        stopSound()
        running = false
        try { stopForeground(STOP_FOREGROUND_DETACH) } catch (e: Exception) { /* يبقى الإشعار (عدّاد +) بعد توقف الصوت */ }
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
    }
}
