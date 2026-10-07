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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
    val totalBytesTransferred: Long = 0L
)

object ProxySharingManager {
    private var socksServer: Socks5ProxyServer? = null
    private var httpServer: HttpProxyServer? = null
    private var pacServer: PacServer? = null

    private val activeConnections = AtomicInteger(0)
    private val totalBytes = AtomicLong(0L)

    private val _state = MutableStateFlow(ProxySharingState())
    val state: StateFlow<ProxySharingState> = _state.asStateFlow()

    fun init(context: Context) {
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

        if (prefs.isSocksEnabled && socksServer == null) {
            socksServer = Socks5ProxyServer(
                context = context,
                port = prefs.socksPort,
                onConnectionChanged = { updateActiveConnections() },
                onBytesTransferred = { bytes -> addBytes(bytes) }
            ).also { it.start() }
        }

        if (prefs.isHttpEnabled && httpServer == null) {
            httpServer = HttpProxyServer(
                context = context,
                port = prefs.httpPort,
                onConnectionChanged = { updateActiveConnections() },
                onBytesTransferred = { bytes -> addBytes(bytes) }
            ).also { it.start() }
        }

        if (prefs.isPacEnabled && pacServer == null) {
            pacServer = PacServer(
                port = prefs.pacPort,
                httpPort = prefs.httpPort,
                socksPort = prefs.socksPort
            ).also { it.start() }
        }

        updateState(context)
    }

    @Synchronized
    fun stopServers(context: Context) {
        ProtonLogger.logCustom(LogCategory.CONN, "Stopping Proxy Sharing servers")
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
                        context = context,
                        port = prefs.socksPort,
                        onConnectionChanged = { updateActiveConnections() },
                        onBytesTransferred = { bytes -> addBytes(bytes) }
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
                        context = context,
                        port = prefs.httpPort,
                        onConnectionChanged = { updateActiveConnections() },
                        onBytesTransferred = { bytes -> addBytes(bytes) }
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

    fun refreshState(context: Context) {
        updateState(context)
    }

    private fun addBytes(bytes: Long) {
        totalBytes.addAndGet(bytes)
        _state.value = _state.value.copy(totalBytesTransferred = totalBytes.get())
    }

    private fun updateActiveConnections() {
        // activeConnections can be updated if servers report counts
        _state.value = _state.value.copy(activeConnections = activeConnections.get())
    }

    private fun updateState(context: Context) {
        val prefs = ProxySharingPreferences(context)
        val ip = NetworkUtils.getLocalIpAddress()
        val isVpn = NetworkUtils.isVpnConnected(context)

        _state.value = ProxySharingState(
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
            totalBytesTransferred = totalBytes.get()
        )
    }
}
