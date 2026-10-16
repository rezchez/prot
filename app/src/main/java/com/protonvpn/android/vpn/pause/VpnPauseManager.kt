/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.vpn.pause

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.protonvpn.android.di.WallClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.toAnyConnectIntent
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class VpnPauseData(
    val isPaused: Boolean = false,
    val deadlineMs: Long = 0L,
    val durationMs: Long = 0L,
    val reconnectIntentData: ConnectIntentData? = null,
    val serverName: String? = null
) : java.io.Serializable

data class VpnPauseState(
    val isPaused: Boolean = false,
    val deadlineMs: Long = 0L,
    val durationMs: Long = 0L,
    val remainingSeconds: Long = 0L,
    val reconnectIntentData: ConnectIntentData? = null,
    val serverName: String? = null
)

@Singleton
class VpnPauseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vpnConnectionManager: Lazy<VpnConnectionManager>,
    private val vpnStateMonitor: VpnStateMonitor,
    @param:WallClock private val clock: () -> Long,
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _pauseState = MutableStateFlow(VpnPauseState())
    val pauseState: StateFlow<VpnPauseState> = _pauseState.asStateFlow()

    private val isReconnecting = AtomicBoolean(false)
    private var tickerJob: Job? = null

    init {
        restoreState()

        // Invalidate/cancel pause if user triggers any non-pause connection
        managerScope.launch {
            vpnStateMonitor.newSessionEvent.collect { (_, trigger) ->
                if (trigger !is ConnectTrigger.Pause && _pauseState.value.isPaused) {
                    ProtonLogger.logCustom(
                        LogCategory.CONN,
                        "Invalidating pending VPN pause reconnect due to new connection trigger: ${trigger.description}"
                    )
                    cancelPause()
                }
            }
        }

        // Cancel pause if user manually requests a permanent disconnect
        managerScope.launch {
            vpnStateMonitor.onDisconnectedByUser.collect {
                if (_pauseState.value.isPaused) {
                    ProtonLogger.logCustom(
                        LogCategory.CONN,
                        "Cancelling VPN pause due to explicit user disconnect"
                    )
                    cancelPause()
                }
            }
        }

        // When VPN successfully connects, clear any stale pause state
        managerScope.launch {
            vpnStateMonitor.status.collectLatest { status ->
                if (status.state == VpnState.Connected && _pauseState.value.isPaused && !isReconnecting.get()) {
                    cancelPause()
                }
            }
        }
    }

    fun isPaused(): Boolean = _pauseState.value.isPaused

    /**
     * Pauses the VPN for the requested duration.
     * Records the reliable deadline, persists metadata, temporarily disconnects,
     * and arms AlarmManager for exact wakeup.
     */
    fun pauseVpn(durationMs: Long) {
        if (durationMs <= 0) return

        val currentParams = vpnStateMonitor.status.value.connectionParams
        val currentIntent = currentParams?.connectIntent as? ConnectIntent
            ?: vpnStateMonitor.connectionIntent as? ConnectIntent
        val intentData = currentIntent?.toData()
        val serverName = currentParams?.server?.serverName ?: currentParams?.server?.displayName

        val now = clock()
        val deadlineMs = now + durationMs
        val remainingSec = (durationMs / 1000L).coerceAtLeast(0L)

        val persistentData = VpnPauseData(
            isPaused = true,
            deadlineMs = deadlineMs,
            durationMs = durationMs,
            reconnectIntentData = intentData,
            serverName = serverName
        )
        saveToStorage(persistentData)
        scheduleAlarm(deadlineMs)

        _pauseState.value = VpnPauseState(
            isPaused = true,
            deadlineMs = deadlineMs,
            durationMs = durationMs,
            remainingSeconds = remainingSec,
            reconnectIntentData = intentData,
            serverName = serverName
        )

        startCountdownTicker()

        ProtonLogger.logCustom(
            LogCategory.CONN,
            "VPN paused for ${durationMs / 60000} minutes. Deadline: $deadlineMs"
        )

        vpnConnectionManager.get().disconnect(DisconnectTrigger.Pause)
    }

    /**
     * Resumes the VPN immediately from a paused state.
     */
    fun resumeVpn() {
        val intentData = _pauseState.value.reconnectIntentData
        cancelPause()

        if (intentData != null) {
            ProtonLogger.logCustom(LogCategory.CONN, "Resuming VPN from pause")
            vpnConnectionManager.get().connectInBackground(
                intentData.toAnyConnectIntent(),
                ConnectTrigger.Pause("user resumed")
            )
        }
    }

    /**
     * Cancels the active pause permanently without reconnecting.
     */
    fun cancelPause() {
        stopCountdownTicker()
        cancelAlarm()
        clearStorage()
        _pauseState.value = VpnPauseState()
        ProtonLogger.logCustom(LogCategory.CONN, "VPN pause cancelled and disarmed")
    }

    /**
     * Triggered when the pause deadline has elapsed.
     */
    fun onPauseExpired() {
        if (!_pauseState.value.isPaused) return
        if (!isReconnecting.compareAndSet(false, true)) return

        try {
            val intentData = _pauseState.value.reconnectIntentData
            stopCountdownTicker()
            cancelAlarm()
            clearStorage()
            _pauseState.value = VpnPauseState()

            if (intentData != null) {
                ProtonLogger.logCustom(
                    LogCategory.CONN,
                    "VPN pause duration expired. Automatically reconnecting..."
                )
                vpnConnectionManager.get().connectInBackground(
                    intentData.toAnyConnectIntent(),
                    ConnectTrigger.Pause("pause expired")
                )
            }
        } finally {
            isReconnecting.set(false)
        }
    }

    private fun restoreState() {
        val saved = loadFromStorage() ?: return
        if (!saved.isPaused) return

        val now = clock()
        if (now >= saved.deadlineMs) {
            ProtonLogger.logCustom(
                LogCategory.CONN,
                "VPN pause deadline expired during downtime/process death. Reconnecting now."
            )
            _pauseState.value = VpnPauseState(
                isPaused = true,
                deadlineMs = saved.deadlineMs,
                durationMs = saved.durationMs,
                remainingSeconds = 0L,
                reconnectIntentData = saved.reconnectIntentData,
                serverName = saved.serverName
            )
            onPauseExpired()
        } else {
            val remainingSec = ((saved.deadlineMs - now) / 1000L).coerceAtLeast(0L)
            _pauseState.value = VpnPauseState(
                isPaused = true,
                deadlineMs = saved.deadlineMs,
                durationMs = saved.durationMs,
                remainingSeconds = remainingSec,
                reconnectIntentData = saved.reconnectIntentData,
                serverName = saved.serverName
            )
            scheduleAlarm(saved.deadlineMs)
            startCountdownTicker()
            ProtonLogger.logCustom(
                LogCategory.CONN,
                "Restored active VPN pause. Remaining: $remainingSec seconds"
            )
        }
    }

    private fun startCountdownTicker() {
        tickerJob?.cancel()
        tickerJob = managerScope.launch {
            while (isActive && _pauseState.value.isPaused) {
                val now = clock()
                val remaining = ((_pauseState.value.deadlineMs - now) / 1000L).coerceAtLeast(0L)
                if (remaining <= 0) {
                    onPauseExpired()
                    break
                } else {
                    _pauseState.value = _pauseState.value.copy(remainingSeconds = remaining)
                }
                delay(1000L)
            }
        }
    }

    private fun stopCountdownTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun scheduleAlarm(deadlineMs: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, VpnPauseReceiver::class.java).apply {
            action = ACTION_PAUSE_EXPIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PAUSE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadlineMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, deadlineMs, pendingIntent)
            }
        } catch (_: SecurityException) {
            // In case exact alarm permission is missing or revoked on Android 12+, fallback to windowed/standard alarm
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, deadlineMs, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    private fun cancelAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, VpnPauseReceiver::class.java).apply {
            action = ACTION_PAUSE_EXPIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PAUSE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun saveToStorage(data: VpnPauseData) {
        Storage.save(data, VpnPauseData::class.java, VpnPauseData.serializer())
    }

    private fun loadFromStorage(): VpnPauseData? {
        return Storage.load(VpnPauseData::class.java, VpnPauseData.serializer())
    }

    private fun clearStorage() {
        Storage.delete(VpnPauseData::class.java)
    }

    companion object {
        const val ACTION_PAUSE_EXPIRED = "com.protonvpn.android.vpn.ACTION_PAUSE_EXPIRED"
        const val REQUEST_CODE_PAUSE = 8472
    }
}
