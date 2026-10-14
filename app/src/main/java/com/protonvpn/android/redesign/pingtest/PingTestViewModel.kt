/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.utils.SharedPreferencesProvider
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import com.protonvpn.android.vpn.pause.VpnPauseManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class PingTestViewModel @Inject constructor(
    private val serverManager: ServerManager2,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnPauseManager: VpnPauseManager,
    private val prefsProvider: SharedPreferencesProvider
) : ViewModel() {

    private val prefs by lazy {
        prefsProvider.getPrefs("ping_test_preferences")
    }

    private val _uiState = MutableStateFlow(PingTestUiState())
    val uiState: StateFlow<PingTestUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null

    init {
        val savedAttempts = prefs.getInt("attempts_per_server", 1).coerceIn(1, 10)
        _uiState.update {
            it.copy(
                attemptsText = savedAttempts.toString(),
                attemptsPerServer = savedAttempts
            )
        }

        // Retrieve and sort servers alphabetically (case-insensitive deterministic)
        viewModelScope.launch {
            val allServers = serverManager.allServersFlow.first()
            val sortedServers = allServers
                .filter { it.online || it.isVisible }
                .sortedBy { it.displayName.lowercase(Locale.ROOT) }

            _uiState.update {
                it.copy(
                    totalCount = sortedServers.size,
                    allTestedItems = sortedServers.map { s -> ServerPingItem(s) }
                )
            }
        }

        // Monitor active VPN state to reflect active connected server in the UI
        viewModelScope.launch {
            vpnStateMonitor.status.collect { status ->
                val connectedServerId = if (status.state == VpnState.Connected) {
                    status.connectionParams?.server?.serverId
                } else null
                _uiState.update { it.copy(activeConnectedServerId = connectedServerId) }
            }
        }
    }

    fun onAttemptsInputChanged(input: String) {
        val digits = input.filter { it.isDigit() }.take(2)
        val parsed = digits.toIntOrNull()
        val error = when {
            digits.isEmpty() -> "Attempt count is required"
            parsed == null || parsed < 1 || parsed > 10 -> "Must be between 1 and 10 attempts"
            else -> null
        }

        val validAttempts = if (error == null && parsed != null) parsed else _uiState.value.attemptsPerServer
        if (error == null && parsed != null) {
            prefs.edit().putInt("attempts_per_server", parsed).apply()
        }

        _uiState.update {
            it.copy(
                attemptsText = digits,
                attemptsPerServer = validAttempts,
                attemptsError = error
            )
        }
    }

    fun onRequestStartTest() {
        if (_uiState.value.isTesting) return
        val attempts = _uiState.value.attemptsPerServer
        val total = _uiState.value.totalCount

        // Estimate ~6s per attempt
        val totalEstSecs = total * attempts * 6
        val estMins = (totalEstSecs / 60).coerceAtLeast(1)
        val estText = "Approx. $estMins minute(s) ($total servers × $attempts attempt(s))"

        _uiState.update {
            it.copy(
                showStartConfirmationDialog = true,
                estimatedDurationText = estText
            )
        }
    }

    fun dismissConfirmationDialog() {
        _uiState.update { it.copy(showStartConfirmationDialog = false) }
    }

    fun startTest(vpnUiDelegate: VpnUiDelegate? = null) {
        dismissConfirmationDialog()
        if (_uiState.value.isTesting) return

        testJob?.cancel()
        testJob = viewModelScope.launch(Dispatchers.IO) {
            val allServers = serverManager.allServersFlow.first()
            // Normalize and sort servers alphabetically by displayed server name (case-insensitive deterministic)
            val sortedServers = allServers
                .filter { it.online || it.isVisible }
                .sortedBy { it.displayName.lowercase(Locale.ROOT) }

            if (sortedServers.isEmpty()) return@launch

            val attempts = _uiState.value.attemptsPerServer
            val initialItems = sortedServers.map { ServerPingItem(it, ServerTestStatus.NOT_TESTED) }

            _uiState.update {
                it.copy(
                    isTesting = true,
                    totalCount = sortedServers.size,
                    testedCount = 0,
                    workingCount = 0,
                    failedCount = 0,
                    workingServers = emptyList(),
                    allTestedItems = initialItems,
                    currentTestingServer = null
                )
            }

            val itemsMap = ConcurrentHashMap<String, ServerPingItem>()
            sortedServers.forEach { itemsMap[it.serverId] = ServerPingItem(it, ServerTestStatus.NOT_TESTED) }

            val workingList = mutableListOf<ServerPingItem>()

            // Ensure Pause feature auto-reconnect does not interfere during ping test
            vpnPauseManager.cancelPause()

            try {
                for ((index, server) in sortedServers.withIndex()) {
                    if (!isActive) break

                    var serverSuccess = false
                    var successfulPing: Long? = null

                    for (attempt in 1..attempts) {
                        if (!isActive) break

                        _uiState.update {
                            it.copy(
                                currentTestingServer = server,
                                currentAttempt = attempt,
                                totalAttempts = attempts,
                                currentStepDescription = "Attempt $attempt/$attempts: Connecting to ${server.displayName}..."
                            )
                        }

                        itemsMap[server.serverId] = ServerPingItem(
                            server = server,
                            status = ServerTestStatus.TESTING_CONNECTING,
                            statusDetail = "Attempt $attempt of $attempts"
                        )
                        updateStateSync(sortedServers, itemsMap, index, workingList)

                        val connectStartTime = SystemClock.elapsedRealtime()
                        val connectIntent = ConnectIntent.fromServer(
                            server,
                            ServerFeature.fromServer(server)
                        )

                        // Initiate real VPN connection via existing connection engine
                        vpnConnectionManager.connectInBackground(
                            connectIntent,
                            ConnectTrigger.Auto("ping test attempt $attempt")
                        )

                        // Confirm connection state was established for this target server
                        val isConnected = waitForConnectionSuccess(server.serverId, timeoutMs = 7000)

                        if (isConnected) {
                            val rtt = SystemClock.elapsedRealtime() - connectStartTime
                            serverSuccess = true
                            successfulPing = rtt.coerceAtLeast(10L)

                            _uiState.update {
                                it.copy(currentStepDescription = "Connected! Teardown temporary test session...")
                            }

                            // Disconnect cleanly after each successful test
                            vpnConnectionManager.disconnectAndWait(DisconnectTrigger.Test("ping test clean teardown"))
                            waitForDisconnected(timeoutMs = 5000)

                            break // Stop attempting this server as soon as successful connection is confirmed
                        } else {
                            // Disconnect failed attempt cleanly
                            vpnConnectionManager.disconnectAndWait(DisconnectTrigger.Test("ping test failed attempt teardown"))
                            waitForDisconnected(timeoutMs = 5000)
                        }
                    }

                    if (serverSuccess) {
                        val item = ServerPingItem(
                            server = server,
                            status = ServerTestStatus.AVAILABLE,
                            pingMs = successfulPing,
                            statusDetail = "Connection verified (${successfulPing}ms)"
                        )
                        itemsMap[server.serverId] = item
                        workingList.add(item)
                    } else {
                        itemsMap[server.serverId] = ServerPingItem(
                            server = server,
                            status = ServerTestStatus.UNAVAILABLE,
                            statusDetail = "Failed after $attempts attempt(s)"
                        )
                    }

                    updateStateSync(sortedServers, itemsMap, index + 1, workingList)
                }
            } finally {
                // Ensure temporary test connection is disconnected on completion or cancellation
                try {
                    if (vpnStateMonitor.status.value.state != VpnState.Disabled) {
                        vpnConnectionManager.disconnectAndWait(DisconnectTrigger.Test("ping test finish"))
                    }
                } catch (_: Exception) {}

                finalizeResults(sortedServers, itemsMap, workingList)
            }
        }
    }

    private suspend fun waitForConnectionSuccess(targetServerId: String, timeoutMs: Long): Boolean {
        val startTime = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - startTime < timeoutMs) {
            val status = vpnStateMonitor.status.value
            if (status.state == VpnState.Connected &&
                status.connectionParams?.server?.serverId == targetServerId
            ) {
                return true
            }
            if (status.state is VpnState.Error) {
                return false
            }
            delay(200)
        }
        return false
    }

    private suspend fun waitForDisconnected(timeoutMs: Long) {
        val startTime = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - startTime < timeoutMs) {
            val status = vpnStateMonitor.status.value
            if (status.state == VpnState.Disabled) {
                return
            }
            delay(200)
        }
    }

    private fun updateStateSync(
        sortedServers: List<Server>,
        itemsMap: Map<String, ServerPingItem>,
        tested: Int,
        workingList: List<ServerPingItem>
    ) {
        _uiState.update { state ->
            state.copy(
                testedCount = tested,
                workingCount = workingList.size,
                failedCount = tested - workingList.size,
                workingServers = workingList.sortedBy { it.server.displayName.lowercase(Locale.ROOT) },
                allTestedItems = sortedServers.map { s -> itemsMap[s.serverId] ?: ServerPingItem(s) }
            )
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (vpnStateMonitor.status.value.state != VpnState.Disabled) {
                    vpnConnectionManager.disconnectAndWait(DisconnectTrigger.Test("ping test cancelled"))
                }
            } catch (_: Exception) {}
        }
        _uiState.update { state ->
            state.copy(
                isTesting = false,
                currentTestingServer = null,
                currentStepDescription = null
            )
        }
    }

    private fun finalizeResults(
        sortedServers: List<Server>,
        itemsMap: Map<String, ServerPingItem>,
        workingList: List<ServerPingItem>
    ) {
        val dateStr = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.US).format(Date())
        val workingSorted = workingList.sortedBy { it.server.displayName.lowercase(Locale.ROOT) }

        _uiState.update { state ->
            state.copy(
                isTesting = false,
                currentTestingServer = null,
                currentStepDescription = null,
                lastTestTime = dateStr,
                workingServers = workingSorted,
                allTestedItems = sortedServers.map { s -> itemsMap[s.serverId] ?: ServerPingItem(s) }
            )
        }
    }

    fun connectToServer(vpnUiDelegate: VpnUiDelegate, server: Server) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val connectIntent = ConnectIntent.fromServer(
                    server,
                    ServerFeature.fromServer(server)
                )
                vpnConnectionManager.connect(
                    vpnUiDelegate,
                    connectIntent,
                    ConnectTrigger.CountriesServer("ping test item connect")
                )
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "Error connecting from ping test: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelTest()
    }
}
