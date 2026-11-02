package com.example.assistant

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder

// पीछे (background) चलता रहता है ताकि स्क्रीन बंद होने पर भी सुन सके
class AssistantService : Service() {
    companion object {
        var engine: Assistant? = null
        const val CH = "assistant"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val e = Assistant(applicationContext)
        e.onQuit = {
            stopForeground(true)
            stopSelf()
        }
        engine = e
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            goForeground()
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        engine?.start()
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "AI Assistant", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stopI = PendingIntent.getService(
            this, 1, Intent(this, AssistantService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CH) else Notification.Builder(this)
        b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
        b.setContentTitle("AI Assistant chal raha hai")
        b.setContentText("Band karne ke liye Stop dabao")
        b.setContentIntent(open)
        b.setOngoing(true)
        b.addAction(Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop", stopI).build())
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    override fun onDestroy() {
        engine?.shutdown()
        engine = null
        super.onDestroy()
    }
}
