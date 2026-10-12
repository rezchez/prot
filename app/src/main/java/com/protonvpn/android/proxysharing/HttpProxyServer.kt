/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.proxysharing

import android.content.Context
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HttpProxyServer(
    context: Context,
    private val port: Int = 10809,
    private val onConnectionChanged: ((activeCount: Int) -> Unit)? = null,
    private val onBytesTransferred: ((bytes: Long) -> Unit)? = null,
    private val onBytesUploaded: ((bytes: Long) -> Unit)? = null,
    private val onBytesDownloaded: ((bytes: Long) -> Unit)? = null
) {
    private val appContext = context.applicationContext
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var executor: ExecutorService? = null
    private val activeConnections = AtomicInteger(0)

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
                ProtonLogger.logCustom(LogCategory.CONN, "HTTP/HTTPS CONNECT Proxy Server listening on 0.0.0.0:$port")

                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor?.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            ProtonLogger.logCustom(LogCategory.CONN, "HTTP proxy accept error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "HTTP proxy server failed to bind to port $port: ${e.message}")
            } finally {
                stop()
            }
        }, "HttpProxyServer-$port").start()
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
        ProtonLogger.logCustom(LogCategory.CONN, "HTTP/HTTPS CONNECT Proxy Server stopped on port $port")
    }

    private fun handleClient(clientSocket: Socket) {
        val currentCount = activeConnections.incrementAndGet()
        onConnectionChanged?.invoke(currentCount)

        try {
            clientSocket.soTimeout = 30000
            val clientIn = BufferedInputStream(clientSocket.getInputStream())
            val clientOut = clientSocket.getOutputStream()

            val requestLine = readLine(clientIn)
            if (requestLine.isNullOrBlank()) {
                clientSocket.close()
                return
            }

            val parts = requestLine.trim().split(" ")
            if (parts.size < 2) {
                clientSocket.close()
                return
            }

            val method = parts[0].uppercase()
            val target = parts[1]
            val protocol = if (parts.size >= 3) parts[2] else "HTTP/1.1"

            if (method == "CONNECT") {
                // HTTPS CONNECT Tunnel
                // target is in format "host:port"
                val hostPort = target.split(":")
                val host = hostPort[0]
                val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443

                // Consume client request headers until empty line
                while (true) {
                    val line = readLine(clientIn)
                    if (line.isNullOrEmpty()) break
                }

                clientSocket.soTimeout = 0

                val prefs = ProxySharingPreferences(appContext)
                val shouldBypass = com.protonvpn.android.utils.IranBypassUtils.shouldBypass(
                    host,
                    prefs.bypassIrEnabled,
                    prefs.customBypassUrl
                )
                val outboundSocket = try {
                    if (shouldBypass) {
                        NetworkUtils.createOutboundDirectSocket(host, port)
                    } else {
                        NetworkUtils.createOutboundTunnelSocket(appContext, host, port)
                    }
                } catch (e: Exception) {
                    ProtonLogger.logCustom(LogCategory.CONN, "HTTP CONNECT to $host:$port failed: ${e.message}")
                    val failResponse = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n"
                    clientOut.write(failResponse.toByteArray(Charsets.ISO_8859_1))
                    clientOut.flush()
                    clientSocket.close()
                    return
                }

                // Send 200 Connection Established to client
                val successResponse = "HTTP/1.1 200 Connection Established\r\nProxy-Agent: ProtonVPN-Proxy\r\n\r\n"
                clientOut.write(successResponse.toByteArray(Charsets.ISO_8859_1))
                clientOut.flush()

                NetworkUtils.pipeSockets(
                    clientSocket = clientSocket,
                    serverSocket = outboundSocket,
                    onBytesTransferred = onBytesTransferred,
                    onBytesUploaded = onBytesUploaded,
                    onBytesDownloaded = onBytesDownloaded
                )
            } else {
                // Regular HTTP proxy (GET, POST, ...
                val headers = mutableListOf<String>()
                var hostHeader: String? = null

                while (true) {
                    val line = readLine(clientIn)
                    if (line.isNullOrEmpty()) break
                    headers.add(line)
                    if (line.startsWith("Host:", ignoreCase = true)) {
                        hostHeader = line.substring(5).trim()
                    }
                }

                val (targetHost, targetPort, relativePath) = resolveHttpTarget(target, hostHeader)
                if (targetHost.isBlank()) {
                    clientSocket.close()
                    return
                }

                clientSocket.soTimeout = 0

                val prefs = ProxySharingPreferences(appContext)
                val shouldBypass = com.protonvpn.android.utils.IranBypassUtils.shouldBypass(
                    targetHost,
                    prefs.bypassIrEnabled,
                    prefs.customBypassUrl
                )
                val outboundSocket = try {
                    if (shouldBypass) {
                        NetworkUtils.createOutboundDirectSocket(targetHost, targetPort)
                    } else {
                        NetworkUtils.createOutboundTunnelSocket(appContext, targetHost, targetPort)
                    }
                } catch (e: Exception) {
                    ProtonLogger.logCustom(LogCategory.CONN, "HTTP request to $targetHost:$targetPort failed: ${e.message}")
                    val failResponse = "HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n"
                    clientOut.write(failResponse.toByteArray(Charsets.ISO_8859_1))
                    clientOut.flush()
                    clientSocket.close()
                    return
                }

                // Forward modified request line and headers
                val outboundOut = outboundSocket.getOutputStream()
                val newRequestLine = "$method $relativePath $protocol\r\n"
                outboundOut.write(newRequestLine.toByteArray(Charsets.ISO_8859_1))

                for (header in headers) {
                    // Filter out proxy-specific headers
                    if (!header.startsWith("Proxy-Connection:", ignoreCase = true)) {
                        outboundOut.write("$header\r\n".toByteArray(Charsets.ISO_8859_1))
                    }
                }
                outboundOut.write("\r\n".toByteArray(Charsets.ISO_8859_1))
                outboundOut.flush()

                NetworkUtils.pipeSockets(
                    clientSocket = clientSocket,
                    serverSocket = outboundSocket,
                    onBytesTransferred = onBytesTransferred,
                    onBytesUploaded = onBytesUploaded,
                    onBytesDownloaded = onBytesDownloaded
                )
            }
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "HTTP proxy client error: ${e.message}")
            try { clientSocket.close() } catch (_: Exception) {}
        } finally {
            val count = activeConnections.decrementAndGet()
            onConnectionChanged?.invoke(count)
        }
    }

    private fun resolveHttpTarget(target: String, hostHeader: String?): Triple<String, Int, String> {
        return try {
            if (target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)) {
                val uri = URI(target)
                val host = uri.host ?: hostHeader?.split(":")?.get(0) ?: ""
                val port = if (uri.port != -1) uri.port else (if (uri.scheme?.equals("https", ignoreCase = true) == true) 443 else 80)
                val rawPath = (if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath) + (if (uri.rawQuery != null) "?${uri.rawQuery}" else "")
                Triple(host, port, rawPath)
            } else {
                val hostParts = (hostHeader ?: "").split(":")
                val host = hostParts[0].trim()
                val port = if (hostParts.size > 1) hostParts[1].trim().toIntOrNull() ?: 80 else 80
                Triple(host, port, target)
            }
        } catch (_: Exception) {
            val hostParts = (hostHeader ?: "").split(":")
            val host = hostParts[0].trim()
            val port = if (hostParts.size > 1) hostParts[1].trim().toIntOrNull() ?: 80 else 80
            Triple(host, port, target)
        }
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
