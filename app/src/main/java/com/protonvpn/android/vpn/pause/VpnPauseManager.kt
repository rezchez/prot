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
import android.content.SharedPreferences
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.toAnyConnectIntent
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.utils.SharedPreferencesProvider
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VpnPauseManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsProvider: SharedPreferencesProvider,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnStateMonitor: VpnStateMonitor,
    private val notificationHelper: dagger.Lazy<NotificationHelper>,
    private val scope: CoroutineScope
) {
    private val prefs: SharedPreferences by lazy {
        prefsProvider.getPrefs(PREFS_NAME)
    }

    private val alarmManager: AlarmManager? by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    }

    private val _pauseState = MutableStateFlow<VpnPauseState>(VpnPauseState.NotPaused)
    val pauseState: StateFlow<VpnPauseState> = _pauseState.asStateFlow()

    private var tickerJob: Job? = null
    private var isReconnectingFromPause = false

    init {
        checkAndRestorePause()

        // Observe VPN state to handle manual user actions while paused
        vpnStateMonitor.status.onEach { status ->
            if (_pauseState.value is VpnPauseState.Paused) {
                if (status.state == VpnState.Connected && !isReconnectingFromPause) {
                    ProtonLogger.logCustom(LogCategory.CONN, "User connected manually while paused. Canceling pause.")
                    cancelPauseInternal(clearPrefs = true, cancelAlarm = true)
                }
            }
        }.launchIn(scope)
    }

    fun pauseVpn(durationMs: Long, currentIntent: AnyConnectIntent?) {
        val now = System.currentTimeMillis()
        val deadlineMs = now + durationMs

        val intentData = currentIntent?.toData() ?: getActiveConnectIntentData()

        val jsonStr = intentData?.let {
            try {
                Json.encodeToString(ConnectIntentData.serializer(), it)
            } catch (e: Exception) {
                null
            }
        }

        prefs.edit()
            .putLong(KEY_DEADLINE, deadlineMs)
            .putLong(KEY_TOTAL_DURATION, durationMs)
            .putString(KEY_SAVED_INTENT, jsonStr)
            .apply()

        scheduleAlarm(deadlineMs)

        ProtonLogger.logCustom(LogCategory.CONN, "Pausing VPN for $durationMs ms until $deadlineMs")

        vpnConnectionManager.disconnect(DisconnectTrigger.QuickConnect("pause"))

        startPauseState(deadlineMs, durationMs, intentData)
    }

    fun resumeVpn() {
        ProtonLogger.logCustom(LogCategory.CONN, "Resuming VPN from pause manually.")
        val savedData = getSavedConnectIntentData()
        cancelPauseInternal(clearPrefs = true, cancelAlarm = true)

        if (savedData != null) {
            isReconnectingFromPause = true
            scope.launch(Dispatchers.Main) {
                try {
                    vpnConnectionManager.connectInBackground(
                        savedData.toAnyConnectIntent(),
                        ConnectTrigger.Auto("Resume from pause")
                    )
                } finally {
                    isReconnectingFromPause = false
                }
            }
        }
    }

    fun cancelPause() {
        ProtonLogger.logCustom(LogCategory.CONN, "Canceling VPN pause manually.")
        cancelPauseInternal(clearPrefs = true, cancelAlarm = true)
    }

    fun onPauseExpired() {
        ProtonLogger.logCustom(LogCategory.CONN, "VPN pause period expired. Auto-reconnecting.")
        val savedData = getSavedConnectIntentData()
        cancelPauseInternal(clearPrefs = true, cancelAlarm = false)

        if (savedData != null) {
            isReconnectingFromPause = true
            scope.launch(Dispatchers.Main) {
                try {
                    vpnConnectionManager.connectInBackground(
                        savedData.toAnyConnectIntent(),
                        ConnectTrigger.Auto("Pause expired auto-reconnect")
                    )
                } finally {
                    isReconnectingFromPause = false
                }
            }
        }
    }

    private fun checkAndRestorePause() {
        val deadlineMs = prefs.getLong(KEY_DEADLINE, 0L)
        val totalDurationMs = prefs.getLong(KEY_TOTAL_DURATION, 0L)

        if (deadlineMs > 0) {
            val now = System.currentTimeMillis()
            if (deadlineMs > now) {
                val savedData = getSavedConnectIntentData()
                ProtonLogger.logCustom(LogCategory.CONN, "Restoring active pause. ${deadlineMs - now} ms remaining.")
                scheduleAlarm(deadlineMs)
                startPauseState(deadlineMs, totalDurationMs, savedData)
            } else {
                ProtonLogger.logCustom(LogCategory.CONN, "Found expired pause on restore. Reconnecting.")
                onPauseExpired()
            }
        }
    }

    private fun startPauseState(deadlineMs: Long, totalDurationMs: Long, savedData: ConnectIntentData?) {
        tickerJob?.cancel()
        tickerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = deadlineMs - now
                if (remaining <= 0) {
                    onPauseExpired()
                    break
                }
                _pauseState.value = VpnPauseState.Paused(
                    deadlineMs = deadlineMs,
                    remainingMs = remaining,
                    totalDurationMs = totalDurationMs,
                    savedConnectIntentData = savedData
                )
                delay(1000)
            }
        }
    }

    private fun cancelPauseInternal(clearPrefs: Boolean, cancelAlarm: Boolean) {
        tickerJob?.cancel()
        tickerJob = null

        if (cancelAlarm) {
            cancelAlarm()
        }

        if (clearPrefs) {
            prefs.edit().clear().apply()
        }

        _pauseState.value = VpnPauseState.NotPaused
    }

    private fun scheduleAlarm(deadlineMs: Long) {
        val pendingIntent = getAlarmPendingIntent() ?: return
        try {
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineMs,
                pendingIntent
            )
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Failed to schedule exact alarm: ${e.message}")
        }
    }

    private fun cancelAlarm() {
        val pendingIntent = getAlarmPendingIntent() ?: return
        try {
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Failed to cancel alarm: ${e.message}")
        }
    }

    private fun getAlarmPendingIntent(): PendingIntent? {
        val intent = Intent(context, VpnPauseReceiver::class.java).apply {
            action = VpnPauseReceiver.ACTION_PAUSE_EXPIRED
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PAUSE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getSavedConnectIntentData(): ConnectIntentData? {
        val jsonStr = prefs.getString(KEY_SAVED_INTENT, null) ?: return null
        return try {
            Json.decodeFromString(ConnectIntentData.serializer(), jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun getActiveConnectIntentData(): ConnectIntentData? {
        return vpnStateMonitor.status.value.connectionParams?.connectIntentData
    }

    companion object {
        private const val PREFS_NAME = "vpn_pause_preferences"
        private const val KEY_DEADLINE = "pause_deadline"
        private const val KEY_TOTAL_DURATION = "pause_total_duration"
        private const val KEY_SAVED_INTENT = "pause_saved_intent"
        private const val REQUEST_CODE_PAUSE = 8801
    }
}
