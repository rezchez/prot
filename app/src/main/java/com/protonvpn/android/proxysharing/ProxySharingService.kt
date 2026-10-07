/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.proxysharing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.protonvpn.android.R
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.app.ui.MainActivity

class ProxySharingService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            ProtonLogger.logCustom(LogCategory.CONN, "ProxySharingService received STOP action")
            val prefs = ProxySharingPreferences(this)
            prefs.isSharingEnabled = false
            ProxySharingManager.stopServers(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Start Foreground Notification
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Start Servers
        ProxySharingManager.startServers(this)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        ProxySharingManager.stopServers(this)
        ProtonLogger.logCustom(LogCategory.CONN, "ProxySharingService destroyed")
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ProtonVPN:ProxySharingWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety timeout
            }
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VPN Proxy Sharing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of local LAN VPN and proxy sharing servers"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val prefs = ProxySharingPreferences(this)
        val ip = NetworkUtils.getLocalIpAddress()
        val socksPort = prefs.socksPort
        val httpPort = prefs.httpPort

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pMainIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, ProxySharingService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val title = "VPN Proxy Sharing Active"
        val content = "LAN IP: $ip | SOCKS5: $socksPort | HTTP: $httpPort"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(pMainIntent)
            .addAction(R.drawable.ic_close, "Stop Sharing", pStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "com.protonvpn.android.proxysharing.START"
        const val ACTION_STOP = "com.protonvpn.android.proxysharing.STOP"
        private const val CHANNEL_ID = "com.protonvpn.android.proxy_sharing"
        private const val NOTIFICATION_ID = 10808
    }
}
