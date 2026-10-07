/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.proxysharing

import android.content.Context
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class Socks5ProxyServer(
    private val context: Context,
    private val port: Int = 10808,
    private val onConnectionChanged: ((activeCount: Int) -> Unit)? = null,
    private val onBytesTransferred: ((bytes: Long) -> Unit)? = null
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var executor: ExecutorService? = null
    private val activeConnections = java.util.concurrent.atomic.AtomicInteger(0)

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)
        executor = Executors.newCachedThreadPool()

        Thread({
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("0.0.0.0", port))
                }
                ProtonLogger.logCustom(LogCategory.CONN, "SOCKS5 Proxy Server listening on 0.0.0.0:$port")

                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor?.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            ProtonLogger.logCustom(LogCategory.CONN, "SOCKS5 accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "SOCKS5 server failed to bind to port $port: ${e.message}")
            } finally {
                stop()
            }
        }, "Socks5Server-$port").start()
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        activeConnections.set(0)
        onConnectionChanged?.invoke(0)
        ProtonLogger.logCustom(LogCategory.CONN, "SOCKS5 Proxy Server stopped on port $port")
    }

    private fun handleClient(clientSocket: Socket) {
        val currentCount = activeConnections.incrementAndGet()
        onConnectionChanged?.invoke(currentCount)

        try {
            clientSocket.soTimeout = 30000
            val input = DataInputStream(clientSocket.getInputStream())
            val output = DataOutputStream(clientSocket.getOutputStream())

            // 1. Version identifier / method selection message
            val version = input.readByte().toInt() and 0xFF
            if (version != 0x05) {
                clientSocket.close()
                return
            }

            val nMethods = input.readByte().toInt() and 0xFF
            val methods = ByteArray(nMethods)
            input.readFully(methods)

            // Select method 0x00 (NO AUTHENTICATION REQUIRED)
            output.writeByte(0x05)
            output.writeByte(0x00)
            output.flush()

            // 2. Request details
            val reqVersion = input.readByte().toInt() and 0xFF
            if (reqVersion != 0x05) {
                clientSocket.close()
                return
            }

            val cmd = input.readByte().toInt() and 0xFF
            input.readByte() // RSV (0x00)
            val atyp = input.readByte().toInt() and 0xFF

            if (cmd != 0x01) {
                // Command not supported
                sendReply(output, 0x07)
                clientSocket.close()
                return
            }

            val targetHost: String = when (atyp) {
                0x01 -> { // IPv4
                    val ipBytes = ByteArray(4)
                    input.readFully(ipBytes)
                    InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                0x03 -> { // Domain name
                    val domainLen = input.readByte().toInt() and 0xFF
                    val domainBytes = ByteArray(domainLen)
                    input.readFully(domainBytes)
                    String(domainBytes, Charsets.UTF_8)
                }
                0x04 -> { // IPv6
                    val ipBytes = ByteArray(16)
                    input.readFully(ipBytes)
                    InetAddress.getByAddress(ipBytes).hostAddress ?: ""
                }
                else -> {
                    sendReply(output, 0x08) // Address type not supported
                    clientSocket.close()
                    return
                }
            }

            val targetPort = input.readUnsignedShort()

            // Reset timeout for streaming
            clientSocket.soTimeout = 0

            // 3. Connect to target through active VPN tunnel
            var outboundSocket: Socket? = null
            try {
                outboundSocket = NetworkUtils.createOutboundTunnelSocket(context, targetHost, targetPort)
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "SOCKS5 connect to $targetHost:$targetPort failed: ${e.message}")
                sendReply(output, 0x05) // Connection refused
                clientSocket.close()
                return
            }

            // 4. Send success reply
            sendReply(output, 0x00)

            // 5. Pipe sockets bidirectionally
            NetworkUtils.pipeSockets(clientSocket, outboundSocket, onBytesTransferred)
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "SOCKS5 client error: ${e.message}")
            try { clientSocket.close() } catch (_: Exception) {}
        } finally {
            val count = activeConnections.decrementAndGet()
            onConnectionChanged?.invoke(count)
        }
    }

    private fun sendReply(output: DataOutputStream, status: Int) {
        output.writeByte(0x05) // VER
        output.writeByte(status) // REP
        output.writeByte(0x00) // RSV
        output.writeByte(0x01) // ATYP IPv4
        output.write(byteArrayOf(0, 0, 0, 0)) // BND.ADDR 0.0.0.0
        output.writeShort(0) // BND.PORT 0
        output.flush()
    }
}
