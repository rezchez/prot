/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.utils

import android.os.SystemClock
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.LinkedHashMap

sealed interface PingResult {
    data object Idle : PingResult
    data object Loading : PingResult
    data class Success(val latencyMs: Long) : PingResult
    data class Error(val message: String = "Offline") : PingResult
}

object ServerPingManager {

    private const val MAX_CACHE_ENTRIES = 256

    // Bounded LRU cache to prevent memory leaks from unbounded ConcurrentHashMap growth
    private val resultMap = Collections.synchronizedMap(
        object : LinkedHashMap<String, MutableStateFlow<PingResult>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableStateFlow<PingResult>>?): Boolean {
                return size > MAX_CACHE_ENTRIES
            }
        }
    )

    fun getPingState(host: String): StateFlow<PingResult> {
        val clean = IranBypassUtils.cleanHost(host)
        return synchronized(resultMap) {
            resultMap.getOrPut(clean) { MutableStateFlow(PingResult.Idle) }.asStateFlow()
        }
    }

    suspend fun ping(host: String): PingResult = withContext(Dispatchers.IO) {
        val clean = IranBypassUtils.cleanHost(host)
        if (clean.isBlank()) return@withContext PingResult.Error("Invalid host")

        val flow = synchronized(resultMap) {
            resultMap.getOrPut(clean) { MutableStateFlow(PingResult.Idle) }
        }
        flow.value = PingResult.Loading

        ProtonLogger.logCustom(LogCategory.CONN, "Pinging server host: $clean")

        // 1. Try ICMP ping first
        val icmpResult = pingIcmp(clean)
        if (icmpResult != null) {
            val res = PingResult.Success(icmpResult)
            flow.value = res
            return@withContext res
        }

        // 2. Fallback to TCP ping on ports 443, 80
        val tcpResult = pingTcp(clean, 443) ?: pingTcp(clean, 80)
        val finalResult = if (tcpResult != null) {
            PingResult.Success(tcpResult)
        } else {
            PingResult.Error("Offline")
        }

        flow.value = finalResult
        finalResult
    }

    private fun pingIcmp(host: String, timeoutSec: Int = 2): Long? {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -W $timeoutSec $host")
            var rtt: Long? = null
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.contains("time=")) {
                        val timePart = currentLine.substringAfter("time=").substringBefore(" ms").trim()
                        rtt = timePart.toDoubleOrNull()?.toLong()
                    } else if (currentLine.contains("min/avg/max")) {
                        val avgPart = currentLine.substringAfter("=").split("/").getOrNull(1)?.trim()
                        rtt = avgPart?.toDoubleOrNull()?.toLong()
                    }
                }
            }
            val exitCode = process.waitFor()
            if (exitCode == 0 && rtt != null) rtt else null
        } catch (_: Exception) {
            null
        } finally {
            try {
                process?.destroy()
            } catch (_: Exception) {}
        }
    }

    private fun pingTcp(host: String, port: Int, timeoutMs: Int = 2500): Long? {
        return try {
            val start = SystemClock.elapsedRealtime()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            SystemClock.elapsedRealtime() - start
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        synchronized(resultMap) {
            resultMap.clear()
        }
    }
}
