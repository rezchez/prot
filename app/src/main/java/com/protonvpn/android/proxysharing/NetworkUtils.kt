/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.proxysharing

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

object NetworkUtils {

    /**
     * Finds the primary local IPv4 address (Wi-Fi or Ethernet LAN IP).
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // First look for active WLAN or ETH interfaces
            val preferredInterfaces = interfaces.filter { iface ->
                iface.isUp && !iface.isLoopback && !iface.name.startsWith("tun") &&
                    !iface.name.startsWith("dummy") && !iface.name.startsWith("p2p")
            }

            // Prioritize wlan and eth
            val sorted = preferredInterfaces.sortedByDescending { iface ->
                val name = iface.name.lowercase()
                when {
                    name.startsWith("wlan") -> 3
                    name.startsWith("eth") || name.startsWith("en") -> 2
                    name.startsWith("ap") -> 1
                    else -> 0
                }
            }

            for (iface in sorted) {
                val addresses = Collections.list(iface.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Error finding local IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    /**
     * Returns the active VPN Network from ConnectivityManager, if connected.
     */
    fun getActiveVpnNetwork(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return network
            }
        }
        return null
    }

    /**
     * Checks if the Android system currently has an active VPN network.
     */
    fun isVpnConnected(context: Context): Boolean {
        return getActiveVpnNetwork(context) != null
    }

    /**
     * Creates an outbound TCP socket bound to the active VPN tunnel network if available,
     * ensuring outbound traffic routes through the VPN tunnel.
     */
    fun createOutboundTunnelSocket(
        context: Context,
        host: String,
        port: Int,
        timeoutMs: Int = 15000
    ): Socket {
        val socket = Socket()
        val vpnNetwork = getActiveVpnNetwork(context)
        if (vpnNetwork != null) {
            try {
                vpnNetwork.bindSocket(socket)
                ProtonLogger.logCustom(LogCategory.CONN, "Bound outbound socket to VPN network for $host:$port")
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "Could not bind socket to VPN network: ${e.message}")
            }
        }
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        return socket
    }

    /**
     * Creates an outbound TCP socket that bypasses the VPN tunnel (direct connection).
     */
    fun createOutboundDirectSocket(
        host: String,
        port: Int,
        timeoutMs: Int = 15000
    ): Socket {
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(host, port), timeoutMs)
        return socket
    }

    /**
     * Transfers data bidirectionally between two sockets in separate threads.
     * Closes both sockets when either stream ends or encounters an exception.
     */
    fun pipeSockets(
        clientSocket: Socket,
        serverSocket: Socket,
        onBytesTransferred: ((Long) -> Unit)? = null,
        onBytesUploaded: ((Long) -> Unit)? = null,
        onBytesDownloaded: ((Long) -> Unit)? = null,
        shouldAbort: (() -> Boolean)? = null
    ) {
        val isClosed = AtomicBoolean(false)

        val closeBoth = {
            if (isClosed.compareAndSet(false, true)) {
                try { clientSocket.close() } catch (_: Exception) {}
                try { serverSocket.close() } catch (_: Exception) {}
            }
        }

        val forward = { input: InputStream, output: OutputStream, onSpecificBytes: ((Long) -> Unit)? ->
            val buffer = ByteArray(8192)
            try {
                while (!isClosed.get()) {
                    if (shouldAbort?.invoke() == true) {
                        break
                    }
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                    val count = bytesRead.toLong()
                    onBytesTransferred?.invoke(count)
                    onSpecificBytes?.invoke(count)
                }
            } catch (_: Exception) {
            } finally {
                closeBoth()
            }
        }

        val thread1 = Thread({ forward(clientSocket.getInputStream(), serverSocket.getOutputStream(), onBytesUploaded) }, "ProxyPipe-C2S")
        val thread2 = Thread({ forward(serverSocket.getInputStream(), clientSocket.getOutputStream(), onBytesDownloaded) }, "ProxyPipe-S2C")

        thread1.start()
        thread2.start()
    }
}
