package com.blague.russe

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Typeface
import android.media.*
import android.os.*
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        val go = Button(this).apply {
            text = "RUSSIAN JOKE\nARE YOU SURE?"
            textSize = 28f
            setBackgroundColor(0xFFCC0000.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { startForegroundService(Intent(this@MainActivity, JokeService::class.java)) }
        }
        val stop = Button(this).apply {
            text = "ARRÊTER"
            setOnClickListener { startActivity(Intent(this@MainActivity, StopActivity::class.java)) }
        }
        col.addView(go, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(stop, LinearLayout.LayoutParams(-1, -2))
        setContentView(col)
    }
}

class StopActivity : Activity() {
    private var target = ""
    private fun newCode() = (1..15).map { "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".random() }.joinToString("")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        target = newCode()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 80, 40, 40) }
        val tv = TextView(this).apply { text = target; textSize = 26f; typeface = Typeface.MONOSPACE }
        val et = EditText(this).apply { hint = "Recopie le code"; isSingleLine = true }
        val ok = Button(this).apply {
            text = "VALIDER"
            setOnClickListener {
                if (et.text.toString() == target) {
                    stopService(Intent(this@StopActivity, JokeService::class.java))
                    finish()
                } else {
                    target = newCode(); tv.text = target; et.setText("")
                    Toast.makeText(this@StopActivity, "Faux ! Nouveau code", Toast.LENGTH_SHORT).show()
                }
            }
        }
        col.addView(tv); col.addView(et); col.addView(ok)
        setContentView(col)
    }
}

class JokeService : Service() {
    private var mp: MediaPlayer? = null
    private val h = Handler(Looper.getMainLooper())
    private var step = 0

    private val ramp = object : Runnable {
        override fun run() {
            step++
            val v = minOf(0.05f + step / 60f, 1f)   // 0 -> max en environ 2 minutes
            mp?.setVolume(v, v)
            try {
                val am = getSystemService(AudioManager::class.java)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * (0.3f + 0.4f * v)).toInt(), 0)
            } catch (e: Exception) {}
            h.postDelayed(this, 2000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (mp != null) return START_STICKY
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("j", "Blague", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, StopActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "j")
            .setContentTitle("Blague en cours... touche pour arrêter")
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pi).setOngoing(true).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        mp = MediaPlayer.create(this, R.raw.joke)?.apply {
            isLooping = true
            setWakeMode(this@JokeService, PowerManager.PARTIAL_WAKE_LOCK)
            setVolume(0.05f, 0.05f)
            start()
        }
        h.post(ramp)
        return START_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(ramp)
        mp?.release(); mp = null
        super.onDestroy()
    }
}
