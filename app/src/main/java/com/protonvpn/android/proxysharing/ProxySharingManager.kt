/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.proxysharing

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class SpeedDataPoint(
    val timestamp: Long,
    val downloadSpeedMbps: Float,
    val uploadSpeedMbps: Float
)

data class ProxySharingState(
    val isSharingEnabled: Boolean = false,
    val isSocksEnabled: Boolean = true,
    val isHttpEnabled: Boolean = true,
    val isPacEnabled: Boolean = true,
    val localIp: String = "127.0.0.1",
    val socksPort: Int = ProxySharingPreferences.DEFAULT_SOCKS_PORT,
    val httpPort: Int = ProxySharingPreferences.DEFAULT_HTTP_PORT,
    val pacPort: Int = ProxySharingPreferences.DEFAULT_PAC_PORT,
    val isSocksRunning: Boolean = false,
    val isHttpRunning: Boolean = false,
    val isPacRunning: Boolean = false,
    val isVpnConnected: Boolean = false,
    val activeConnections: Int = 0,
    val totalBytesTransferred: Long = 0L,
    val totalBytesDownloaded: Long = 0L,
    val totalBytesUploaded: Long = 0L,
    val currentDownloadSpeedMbps: Float = 0f,
    val currentUploadSpeedMbps: Float = 0f,
    val speedHistory: List<SpeedDataPoint> = emptyList(),
    val bypassIrEnabled: Boolean = false,
    val customBypassUrl: String? = null
)

object ProxySharingManager {
    private var socksServer: Socks5ProxyServer? = null
    private var httpServer: HttpProxyServer? = null
    private var pacServer: PacServer? = null

    private val activeConnections = AtomicInteger(0)
    private val totalBytes = AtomicLong(0L)
    private val totalBytesDownloaded = AtomicLong(0L)
    private val totalBytesUploaded = AtomicLong(0L)

    private val speedHistoryList = mutableListOf<SpeedDataPoint>()
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var statsJob: Job? = null

    private val _state = MutableStateFlow(ProxySharingState())
    val state: StateFlow<ProxySharingState> = _state.asStateFlow()

    fun init(context: Context) {
        ConnectedDeviceManager.init(context)
        val prefs = ProxySharingPreferences(context)
        refreshState(context)
        if (prefs.isSharingEnabled) {
            startService(context)
        }
    }

    fun startService(context: Context) {
        val intent = Intent(context, ProxySharingService::class.java).apply {
            action = ProxySharingService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopService(context: Context) {
        val intent = Intent(context, ProxySharingService::class.java).apply {
            action = ProxySharingService.ACTION_STOP
        }
        context.startService(intent)
    }

    @Synchronized
    fun startServers(context: Context) {
        val prefs = ProxySharingPreferences(context)
        val ip = NetworkUtils.getLocalIpAddress()
        val vpnActive = NetworkUtils.isVpnConnected(context)

        ProtonLogger.logCustom(LogCategory.CONN, "Starting Proxy Sharing servers on LAN IP: $ip (VPN active: $vpnActive)")

        // Reset stats for new session
        resetStatisticsInternal()

        if (prefs.isSocksEnabled && socksServer == null) {
            socksServer = Socks5ProxyServer(
                context = context.applicationContext,
                port = prefs.socksPort,
                onConnectionChanged = { updateActiveConnections() },
                onBytesTransferred = null,
                onBytesUploaded = { bytes -> addUploadedBytes(bytes) },
                onBytesDownloaded = { bytes -> addDownloadedBytes(bytes) }
            ).also { it.start() }
        }

        if (prefs.isHttpEnabled && httpServer == null) {
            httpServer = HttpProxyServer(
                context = context.applicationContext,
                port = prefs.httpPort,
                onConnectionChanged = { updateActiveConnections() },
                onBytesTransferred = null,
                onBytesUploaded = { bytes -> addUploadedBytes(bytes) },
                onBytesDownloaded = { bytes -> addDownloadedBytes(bytes) }
            ).also { it.start() }
        }

        if (prefs.isPacEnabled && pacServer == null) {
            pacServer = PacServer(
                port = prefs.pacPort,
                httpPort = prefs.httpPort,
                socksPort = prefs.socksPort
            ).also { it.start() }
        }

        startStatsMonitoring()
        updateState(context)
    }

    @Synchronized
    fun stopServers(context: Context) {
        ProtonLogger.logCustom(LogCategory.CONN, "Stopping Proxy Sharing servers")
        stopStatsMonitoring()

        socksServer?.stop()
        socksServer = null

        httpServer?.stop()
        httpServer = null

        pacServer?.stop()
        pacServer = null

        activeConnections.set(0)
        updateState(context)
    }

    fun toggleMaster(context: Context, enabled: Boolean) {
        val prefs = ProxySharingPreferences(context)
        prefs.isSharingEnabled = enabled
        if (enabled) {
            startService(context)
        } else {
            stopService(context)
        }
        updateState(context)
    }

    fun toggleSocks(context: Context, enabled: Boolean) {
        val prefs = ProxySharingPreferences(context)
        prefs.isSocksEnabled = enabled
        if (prefs.isSharingEnabled) {
            if (enabled) {
                if (socksServer == null) {
                    socksServer = Socks5ProxyServer(
                        context = context.applicationContext,
                        port = prefs.socksPort,
                        onConnectionChanged = { updateActiveConnections() },
                        onBytesTransferred = null,
                        onBytesUploaded = { bytes -> addUploadedBytes(bytes) },
                        onBytesDownloaded = { bytes -> addDownloadedBytes(bytes) }
                    ).also { it.start() }
                }
            } else {
                socksServer?.stop()
                socksServer = null
            }
        }
        updateState(context)
    }

    fun toggleHttp(context: Context, enabled: Boolean) {
        val prefs = ProxySharingPreferences(context)
        prefs.isHttpEnabled = enabled
        if (prefs.isSharingEnabled) {
            if (enabled) {
                if (httpServer == null) {
                    httpServer = HttpProxyServer(
                        context = context.applicationContext,
                        port = prefs.httpPort,
                        onConnectionChanged = { updateActiveConnections() },
                        onBytesTransferred = null,
                        onBytesUploaded = { bytes -> addUploadedBytes(bytes) },
                        onBytesDownloaded = { bytes -> addDownloadedBytes(bytes) }
                    ).also { it.start() }
                }
            } else {
                httpServer?.stop()
                httpServer = null
            }
        }
        updateState(context)
    }

    fun togglePac(context: Context, enabled: Boolean) {
        val prefs = ProxySharingPreferences(context)
        prefs.isPacEnabled = enabled
        if (prefs.isSharingEnabled) {
            if (enabled) {
                if (pacServer == null) {
                    pacServer = PacServer(
                        port = prefs.pacPort,
                        httpPort = prefs.httpPort,
                        socksPort = prefs.socksPort
                    ).also { it.start() }
                }
            } else {
                pacServer?.stop()
                pacServer = null
            }
        }
        updateState(context)
    }

    fun setSocksPort(context: Context, port: Int) {
        val prefs = ProxySharingPreferences(context)
        if (prefs.socksPort == port) return
        prefs.socksPort = port
        if (prefs.isSharingEnabled && prefs.isSocksEnabled) {
            socksServer?.stop()
            socksServer = Socks5ProxyServer(
                context = context.applicationContext,
                port = port,
                onConnectionChanged = { updateActiveConnections() },
                onBytesTransferred = null,
                onBytesUploaded = { bytes -> addUploadedBytes(bytes) },
                onBytesDownloaded = { bytes -> addDownloadedBytes(bytes) }
            ).also { it.start() }
            if (prefs.isPacEnabled && pacServer != null) {
                pacServer?.stop()
                pacServer = PacServer(
                    port = prefs.pacPort,
                    httpPort = prefs.httpPort,
                    socksPort = port
                ).also { it.start() }
            }
        }
        updateState(context)
    }

    fun setHttpPort(context: Context, port: Int) {
        val prefs = ProxySharingPreferences(context)
        if (prefs.httpPort == port) return
        prefs.httpPort = port
        if (prefs.isSharingEnabled && prefs.isHttpEnabled) {
            httpServer?.stop()
            httpServer = HttpProxyServer(
                context = context.applicationContext,
                port = port,
                onConnectionChanged = { updateActiveConnections() },
                onBytesTransferred = null,
                onBytesUploaded = { bytes -> addUploadedBytes(bytes) },
                onBytesDownloaded = { bytes -> addDownloadedBytes(bytes) }
            ).also { it.start() }
            if (prefs.isPacEnabled && pacServer != null) {
                pacServer?.stop()
                pacServer = PacServer(
                    port = prefs.pacPort,
                    httpPort = port,
                    socksPort = prefs.socksPort
                ).also { it.start() }
            }
        }
        updateState(context)
    }

    fun toggleBypassIr(context: Context, enabled: Boolean) {
        val prefs = ProxySharingPreferences(context)
        prefs.bypassIrEnabled = enabled
        updateState(context)
    }

    fun setCustomBypassUrl(context: Context, url: String?) {
        val prefs = ProxySharingPreferences(context)
        prefs.customBypassUrl = url
        updateState(context)
    }

    fun refreshState(context: Context) {
        updateState(context)
    }

    fun resetStatistics(context: Context) {
        resetStatisticsInternal()
        updateState(context)
    }

    private fun resetStatisticsInternal() {
        totalBytes.set(0L)
        totalBytesDownloaded.set(0L)
        totalBytesUploaded.set(0L)
        synchronized(speedHistoryList) {
            speedHistoryList.clear()
        }
        _state.value = _state.value.copy(
            totalBytesTransferred = 0L,
            totalBytesDownloaded = 0L,
            totalBytesUploaded = 0L,
            currentDownloadSpeedMbps = 0f,
            currentUploadSpeedMbps = 0f,
            speedHistory = emptyList()
        )
    }

    private fun startStatsMonitoring() {
        statsJob?.cancel()
        statsJob = managerScope.launch {
            var lastDown = totalBytesDownloaded.get()
            var lastUp = totalBytesUploaded.get()
            var lastTime = System.currentTimeMillis()

            while (isActive) {
                delay(1000L)
                val now = System.currentTimeMillis()
                val currentDown = totalBytesDownloaded.get()
                val currentUp = totalBytesUploaded.get()
                val timeDeltaSec = (now - lastTime) / 1000f

                if (timeDeltaSec > 0f) {
                    val downBytesPerSec = (currentDown - lastDown) / timeDeltaSec
                    val upBytesPerSec = (currentUp - lastUp) / timeDeltaSec
                    // Mbps = (bytes * 8) / 1,000,000
                    val downMbps = (downBytesPerSec * 8f) / 1_000_000f
                    val upMbps = (upBytesPerSec * 8f) / 1_000_000f

                    lastDown = currentDown
                    lastUp = currentUp
                    lastTime = now

                    synchronized(speedHistoryList) {
                        speedHistoryList.add(SpeedDataPoint(now, downMbps, upMbps))
                        if (speedHistoryList.size > 30) {
                            speedHistoryList.removeAt(0)
                        }
                        val historyCopy = speedHistoryList.toList()
                        _state.value = _state.value.copy(
                            currentDownloadSpeedMbps = downMbps,
                            currentUploadSpeedMbps = upMbps,
                            speedHistory = historyCopy,
                            totalBytesTransferred = totalBytes.get(),
                            totalBytesDownloaded = currentDown,
                            totalBytesUploaded = currentUp
                        )
                    }
                }
            }
        }
    }

    private fun stopStatsMonitoring() {
        statsJob?.cancel()
        statsJob = null
        _state.value = _state.value.copy(
            currentDownloadSpeedMbps = 0f,
            currentUploadSpeedMbps = 0f
        )
    }

    private fun addUploadedBytes(bytes: Long) {
        totalBytesUploaded.addAndGet(bytes)
        totalBytes.addAndGet(bytes)
        _state.value = _state.value.copy(
            totalBytesUploaded = totalBytesUploaded.get(),
            totalBytesTransferred = totalBytes.get()
        )
    }

    private fun addDownloadedBytes(bytes: Long) {
        totalBytesDownloaded.addAndGet(bytes)
        totalBytes.addAndGet(bytes)
        _state.value = _state.value.copy(
            totalBytesDownloaded = totalBytesDownloaded.get(),
            totalBytesTransferred = totalBytes.get()
        )
    }

    private fun updateActiveConnections() {
        _state.value = _state.value.copy(activeConnections = activeConnections.get())
    }

    private fun updateState(context: Context) {
        val prefs = ProxySharingPreferences(context)
        val ip = NetworkUtils.getLocalIpAddress()
        val isVpn = NetworkUtils.isVpnConnected(context)

        val historyCopy = synchronized(speedHistoryList) { speedHistoryList.toList() }

        _state.value = _state.value.copy(
            isSharingEnabled = prefs.isSharingEnabled,
            isSocksEnabled = prefs.isSocksEnabled,
            isHttpEnabled = prefs.isHttpEnabled,
            isPacEnabled = prefs.isPacEnabled,
            localIp = ip,
            socksPort = prefs.socksPort,
            httpPort = prefs.httpPort,
            pacPort = prefs.pacPort,
            isSocksRunning = socksServer != null,
            isHttpRunning = httpServer != null,
            isPacRunning = pacServer != null,
            isVpnConnected = isVpn,
            activeConnections = activeConnections.get(),
            totalBytesTransferred = totalBytes.get(),
            totalBytesDownloaded = totalBytesDownloaded.get(),
            totalBytesUploaded = totalBytesUploaded.get(),
            speedHistory = historyCopy,
            bypassIrEnabled = prefs.bypassIrEnabled,
            customBypassUrl = prefs.customBypassUrl
        )
    }
}
