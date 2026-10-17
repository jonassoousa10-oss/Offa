package com.nexora.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder

/** Minimal foreground keeper for the active user-initiated VPN session. */
class ConnectionService : Service() {
    private val channel = "nexora_vpn_status"
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            VpnSession.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(channel, "Conexão VPN", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Mostra quando o túnel VPN está em uso"
            ch.enableLights(false)
            ch.lightColor = Color.GREEN
            manager.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingImmutable()
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingImmutable()
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel)
                      else Notification.Builder(this)
        val notification = builder
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Nexora VPN conectada")
            .setContentText("Túnel criptografado ativo")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Desconectar", stop).build())
            .build()
        startForeground(121, notification)
        return START_STICKY
    }

    private fun pendingImmutable(): Int =
        if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    companion object { const val ACTION_DISCONNECT = "com.nexora.vpn.DISCONNECT" }
}
