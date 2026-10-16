/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.proxysharing

import android.content.Context
import android.content.SharedPreferences
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Serializable
data class DevicePersistedRecord(
    val ip: String,
    val customName: String? = null,
    val isBlocked: Boolean = false,
    val dailyLimitBytes: Long = 0L,
    val totalDownloadedBytes: Long = 0L,
    val totalUploadedBytes: Long = 0L,
    val dailyBytesTransferred: Long = 0L,
    val dailyUsageDate: String = "",
    val firstSeenTimeMs: Long = 0L,
    val lastActiveTimeMs: Long = 0L
)

data class ConnectedDevice(
    val ip: String,
    val customName: String? = null,
    val firstConnectedTimeMs: Long = System.currentTimeMillis(),
    val lastActiveTimeMs: Long = System.currentTimeMillis(),
    val bytesDownloaded: Long = 0L,
    val bytesUploaded: Long = 0L,
    val isBlocked: Boolean = false,
    val dailyLimitBytes: Long = 0L, // 0 means no limit
    val dailyBytesTransferred: Long = 0L,
    val dailyUsageDate: String = "",
    val activeConnectionsCount: Int = 0
) {
    val displayName: String
        get() = customName?.takeIf { it.isNotBlank() } ?: "Device ($ip)"

    val totalBytesTransferred: Long
        get() = bytesDownloaded + bytesUploaded

    val isLimitExceeded: Boolean
        get() = dailyLimitBytes > 0L && dailyBytesTransferred >= dailyLimitBytes

    val isCurrentlyConnected: Boolean
        get() = activeConnectionsCount > 0
}

object ConnectedDeviceManager {
    private const val PREFS_NAME = "proton_vpn_proxy_sharing_devices"
    private const val KEY_DEVICES_JSON = "connected_devices_json"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val devicesMap = ConcurrentHashMap<String, ConnectedDevice>()
    private val activeConnectionsPerIp = ConcurrentHashMap<String, AtomicInteger>()

    private val _devicesFlow = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val devicesFlow: StateFlow<List<ConnectedDevice>> = _devicesFlow.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadPersistedDevices()
    }

    private fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun isBlocked(ip: String): Boolean {
        return devicesMap[ip]?.isBlocked == true
    }

    fun isDailyLimitExceeded(ip: String): Boolean {
        val device = devicesMap[ip] ?: return false
        val today = getTodayDate()
        if (device.dailyUsageDate != today) {
            // New day; limit has reset
            return false
        }
        return device.isLimitExceeded
    }

    fun recordClientConnected(ip: String) {
        val counter = activeConnectionsPerIp.computeIfAbsent(ip) { AtomicInteger(0) }
        val activeCount = counter.incrementAndGet()
        val now = System.currentTimeMillis()
        val today = getTodayDate()

        devicesMap.compute(ip) { _, existing ->
            if (existing != null) {
                val dailyBytes = if (existing.dailyUsageDate == today) existing.dailyBytesTransferred else 0L
                existing.copy(
                    lastActiveTimeMs = now,
                    activeConnectionsCount = activeCount,
                    dailyBytesTransferred = dailyBytes,
                    dailyUsageDate = today
                )
            } else {
                ConnectedDevice(
                    ip = ip,
                    firstConnectedTimeMs = now,
                    lastActiveTimeMs = now,
                    dailyUsageDate = today,
                    activeConnectionsCount = activeCount
                )
            }
        }
        publishAndPersist()
    }

    fun recordClientDisconnected(ip: String) {
        val counter = activeConnectionsPerIp[ip]
        val activeCount = counter?.decrementAndGet()?.coerceAtLeast(0) ?: 0
        val now = System.currentTimeMillis()

        devicesMap.computeIfPresent(ip) { _, existing ->
            existing.copy(
                lastActiveTimeMs = now,
                activeConnectionsCount = activeCount
            )
        }
        publishAndPersist()
    }

    fun recordTraffic(ip: String, downloaded: Long, uploaded: Long) {
        if (downloaded <= 0L && uploaded <= 0L) return
        val now = System.currentTimeMillis()
        val today = getTodayDate()
        val totalDelta = downloaded + uploaded

        devicesMap.compute(ip) { _, existing ->
            if (existing != null) {
                val currentDaily = if (existing.dailyUsageDate == today) existing.dailyBytesTransferred else 0L
                existing.copy(
                    lastActiveTimeMs = now,
                    bytesDownloaded = existing.bytesDownloaded + downloaded,
                    bytesUploaded = existing.bytesUploaded + uploaded,
                    dailyBytesTransferred = currentDaily + totalDelta,
                    dailyUsageDate = today
                )
            } else {
                ConnectedDevice(
                    ip = ip,
                    firstConnectedTimeMs = now,
                    lastActiveTimeMs = now,
                    bytesDownloaded = downloaded,
                    bytesUploaded = uploaded,
                    dailyBytesTransferred = totalDelta,
                    dailyUsageDate = today,
                    activeConnectionsCount = activeConnectionsPerIp[ip]?.get() ?: 0
                )
            }
        }
        publishAndPersist()
    }

    fun setDeviceName(ip: String, newName: String) {
        devicesMap.compute(ip) { _, existing ->
            val clean = newName.trim()
            if (existing != null) {
                existing.copy(customName = if (clean.isEmpty()) null else clean)
            } else {
                ConnectedDevice(ip = ip, customName = if (clean.isEmpty()) null else clean)
            }
        }
        publishAndPersist()
    }

    fun setDeviceBlocked(ip: String, blocked: Boolean) {
        devicesMap.compute(ip) { _, existing ->
            if (existing != null) {
                existing.copy(isBlocked = blocked)
            } else {
                ConnectedDevice(ip = ip, isBlocked = blocked)
            }
        }
        publishAndPersist()
    }

    fun setDeviceDailyLimit(ip: String, limitBytes: Long) {
        devicesMap.compute(ip) { _, existing ->
            if (existing != null) {
                existing.copy(dailyLimitBytes = limitBytes.coerceAtLeast(0L))
            } else {
                ConnectedDevice(ip = ip, dailyLimitBytes = limitBytes.coerceAtLeast(0L))
            }
        }
        publishAndPersist()
    }

    fun removeDevice(ip: String) {
        devicesMap.remove(ip)
        activeConnectionsPerIp.remove(ip)
        publishAndPersist()
    }

    private fun publishAndPersist() {
        val today = getTodayDate()
        val list = devicesMap.values.map { dev ->
            if (dev.dailyUsageDate != today) {
                dev.copy(dailyBytesTransferred = 0L, dailyUsageDate = today)
            } else dev
        }.sortedWith(
            compareByDescending<ConnectedDevice> { it.isCurrentlyConnected }
                .thenByDescending { it.lastActiveTimeMs }
        )

        _devicesFlow.value = list

        scope.launch {
            savePersistedDevices(list)
        }
    }

    private fun loadPersistedDevices() {
        val p = prefs ?: return
        val rawJson = p.getString(KEY_DEVICES_JSON, null) ?: return
        try {
            val records = json.decodeFromString<List<DevicePersistedRecord>>(rawJson)
            val today = getTodayDate()
            records.forEach { record ->
                val dailyBytes = if (record.dailyUsageDate == today) record.dailyBytesTransferred else 0L
                val dev = ConnectedDevice(
                    ip = record.ip,
                    customName = record.customName,
                    firstConnectedTimeMs = if (record.firstSeenTimeMs > 0) record.firstSeenTimeMs else System.currentTimeMillis(),
                    lastActiveTimeMs = if (record.lastActiveTimeMs > 0) record.lastActiveTimeMs else System.currentTimeMillis(),
                    bytesDownloaded = record.totalDownloadedBytes,
                    bytesUploaded = record.totalUploadedBytes,
                    isBlocked = record.isBlocked,
                    dailyLimitBytes = record.dailyLimitBytes,
                    dailyBytesTransferred = dailyBytes,
                    dailyUsageDate = today,
                    activeConnectionsCount = 0
                )
                devicesMap[record.ip] = dev
            }
            _devicesFlow.value = devicesMap.values.sortedWith(
                compareByDescending<ConnectedDevice> { it.isCurrentlyConnected }
                    .thenByDescending { it.lastActiveTimeMs }
            )
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Error loading persisted proxy devices: ${e.message}")
        }
    }

    private fun savePersistedDevices(devices: List<ConnectedDevice>) {
        val p = prefs ?: return
        try {
            val records = devices.map { dev ->
                DevicePersistedRecord(
                    ip = dev.ip,
                    customName = dev.customName,
                    isBlocked = dev.isBlocked,
                    dailyLimitBytes = dev.dailyLimitBytes,
                    totalDownloadedBytes = dev.bytesDownloaded,
                    totalUploadedBytes = dev.bytesUploaded,
                    dailyBytesTransferred = dev.dailyBytesTransferred,
                    dailyUsageDate = dev.dailyUsageDate,
                    firstSeenTimeMs = dev.firstConnectedTimeMs,
                    lastActiveTimeMs = dev.lastActiveTimeMs
                )
            }
            val raw = json.encodeToString(records)
            p.edit().putString(KEY_DEVICES_JSON, raw).apply()
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.CONN, "Error saving persisted proxy devices: ${e.message}")
        }
    }
}
