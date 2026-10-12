/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.servers.Server
import com.protonvpn.android.vpn.ProtocolSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Singleton
class PingTestManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val STEALTH_PROBE_TIMEOUT_MS = 2500
        const val CLOUDFLARE_TRACE_TIMEOUT_MS = 3000
        val CLOUDFLARE_ENDPOINTS = listOf(
            "https://1.1.1.1/cdn-cgi/trace",
            "https://1.0.0.1/cdn-cgi/trace",
            "https://www.cloudflare.com/cdn-cgi/trace"
        )

        private val TRUST_ALL_CERTS = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
    }

    /**
     * Resolves the device's physical network (Wi-Fi, Mobile Data, Ethernet),
     * bypassing VPN virtual interfaces when needed.
     */
    fun getPhysicalNetwork(): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val active = cm.activeNetwork
        if (active != null) {
            val caps = cm.getNetworkCapabilities(active)
            if (caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return active
            }
        }
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return network
            }
        }
        return null
    }

    /**
     * Probes whether the Secure Core entry endpoint port is open for Stealth protocol (TLS 443),
     * and performs a TLS handshake to measure real network RTT.
     * Returns the measured RTT in ms, or null if unreachable / timed out.
     */
    suspend fun probeStealthRtt(server: Server, timeoutMs: Int = STEALTH_PROBE_TIMEOUT_MS): Long? = withContext(Dispatchers.IO) {
        val domain = server.connectingDomains.firstOrNull { it.isOnline }
            ?: server.connectingDomains.firstOrNull() ?: return@withContext null

        val targetIp = domain.getEntryIp(ProtocolSelection.STEALTH)
            ?: domain.getEntryIp(null)
            ?: domain.entryDomain
        val targetPort = domain.getEntryPorts(ProtocolSelection.STEALTH)?.firstOrNull() ?: 443

        var rawSocket: Socket? = null
        var sslSocket: SSLSocket? = null
        try {
            val startTime = SystemClock.elapsedRealtime()
            rawSocket = Socket()
            val physicalNet = getPhysicalNetwork()
            if (physicalNet != null) {
                try {
                    physicalNet.bindSocket(rawSocket)
                } catch (_: Exception) {}
            }
            rawSocket.tcpNoDelay = true
            rawSocket.connect(InetSocketAddress(targetIp, targetPort), timeoutMs)
            if (!rawSocket.isConnected) return@withContext null

            val tcpRtt = SystemClock.elapsedRealtime() - startTime

            // Attempt TLS handshake to verify Stealth TLS endpoint listener
            try {
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, TRUST_ALL_CERTS, SecureRandom())
                }
                rawSocket.soTimeout = timeoutMs
                sslSocket = sslContext.socketFactory.createSocket(
                    rawSocket,
                    targetIp,
                    targetPort,
                    true
                ) as SSLSocket
                sslSocket.startHandshake()
                val tlsRtt = SystemClock.elapsedRealtime() - startTime
                tlsRtt
            } catch (_: Exception) {
                // If TLS handshake throws (e.g. custom obfuscation header or cert check), fall back to TCP RTT
                tcpRtt
            }
        } catch (_: Exception) {
            null
        } finally {
            try { sslSocket?.close() } catch (_: Exception) {}
            try { rawSocket?.close() } catch (_: Exception) {}
        }
    }

    suspend fun probeStealthPort(server: Server, timeoutMs: Int = STEALTH_PROBE_TIMEOUT_MS): Boolean {
        return probeStealthRtt(server, timeoutMs) != null
    }

    /**
     * Measures latency to Cloudflare's connection test page (cdn-cgi/trace).
     * Returns RTT in milliseconds if Cloudflare responded with status 200 and valid trace payload.
     */
    suspend fun pingCloudflareTrace(timeoutMs: Int = CLOUDFLARE_TRACE_TIMEOUT_MS): Long? = withContext(Dispatchers.IO) {
        val physicalNet = getPhysicalNetwork()
        for (endpoint in CLOUDFLARE_ENDPOINTS) {
            var conn: HttpURLConnection? = null
            try {
                val startTime = SystemClock.elapsedRealtime()
                val url = URL(endpoint)
                conn = if (physicalNet != null) {
                    try {
                        physicalNet.openConnection(url) as HttpURLConnection
                    } catch (_: Exception) {
                        url.openConnection() as HttpURLConnection
                    }
                } else {
                    url.openConnection() as HttpURLConnection
                }

                conn.apply {
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "ProtonVPN-Stealth-PingTest")
                    setRequestProperty("Connection", "close")
                }

                val responseCode = conn.responseCode
                val elapsedMs = SystemClock.elapsedRealtime() - startTime

                if (responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    if (body.contains("ip=") || body.contains("fl=") || body.contains("ts=")) {
                        ProtonLogger.logCustom(
                            LogCategory.CONN,
                            "Cloudflare ping success via $endpoint: ${elapsedMs}ms"
                        )
                        return@withContext elapsedMs
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.logCustom(
                    LogCategory.CONN,
                    "Cloudflare ping attempt failed on $endpoint: ${e.message}"
                )
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {}
            }
        }
        null
    }
}
