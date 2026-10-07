/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.proxysharing

import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PacServer(
    private val port: Int = 10810,
    private val httpPort: Int = 10809,
    private val socksPort: Int = 10808,
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var executor: ExecutorService? = null

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
                ProtonLogger.logCustom(LogCategory.CONN, "PAC Auto-Config Server listening on 0.0.0.0:$port")

                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor?.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            ProtonLogger.logCustom(LogCategory.CONN, "PAC accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "PAC server failed to bind to port $port: ${e.message}")
            } finally {
                stop()
            }
        }, "PacServer-$port").start()
    }

    fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        executor?.shutdownNow()
        executor = null
        ProtonLogger.logCustom(LogCategory.CONN, "PAC Auto-Config Server stopped on port $port")
    }

    private fun handleClient(clientSocket: Socket) {
        try {
            clientSocket.soTimeout = 5000
            val input = BufferedInputStream(clientSocket.getInputStream())
            val output = clientSocket.getOutputStream()

            // Read headers until end
            while (true) {
                val line = readLine(input)
                if (line.isNullOrEmpty()) break
            }

            val lanIp = NetworkUtils.getLocalIpAddress()
            val pacScript = generatePacScript(lanIp, httpPort, socksPort)
            val pacBytes = pacScript.toByteArray(Charsets.UTF_8)

            val response = StringBuilder()
                .append("HTTP/1.1 200 OK\r\n")
                .append("Content-Type: application/x-ns-proxy-autoconfig\r\n")
                .append("Content-Length: ${pacBytes.size}\r\n")
                .append("Cache-Control: no-cache, no-store, must-revalidate\r\n")
                .append("Access-Control-Allow-Origin: *\r\n")
                .append("Connection: close\r\n\r\n")
                .toString()

            output.write(response.toByteArray(Charsets.ISO_8859_1))
            output.write(pacBytes)
            output.flush()
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "PAC client error: ${e.message}")
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun generatePacScript(lanIp: String, httpPort: Int, socksPort: Int): String {
        return """
            function FindProxyForURL(url, host) {
                // Bypass local destinations
                if (isPlainHostName(host) ||
                    shExpMatch(host, "*.local") ||
                    isInNet(host, "10.0.0.0", "255.0.0.0") ||
                    isInNet(host, "172.16.0.0", "255.240.0.0") ||
                    isInNet(host, "192.168.0.0", "255.255.0.0") ||
                    isInNet(host, "127.0.0.0", "255.0.0.0")) {
                    return "DIRECT";
                }
                // Route through local LAN VPN proxy
                return "PROXY $lanIp:$httpPort; SOCKS5 $lanIp:$socksPort; DIRECT";
            }
        """.trimIndent() + "\n"
    }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var c = input.read()
        if (c == -1) return null
        while (c != -1 && c != '\n'.code) {
            if (c != '\r'.code) {
                out.write(c)
            }
            c = input.read()
        }
        return out.toString(Charsets.ISO_8859_1.name())
    }
}
