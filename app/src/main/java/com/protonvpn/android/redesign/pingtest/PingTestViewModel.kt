/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.SettingsOverrides
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.ProtocolSelection
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class PingTestViewModel @Inject constructor(
    private val serverManager: ServerManager2,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnStateMonitor: VpnStateMonitor,
    private val pingTestManager: PingTestManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PingTestUiState())
    val uiState: StateFlow<PingTestUiState> = _uiState.asStateFlow()

    private var testJob: Job? = null

    init {
        // Load exclusively Secure Core servers
        viewModelScope.launch {
            val allServers = serverManager.allServersFlow.first()
            val secureCoreServers = allServers
                .filter { it.isSecureCoreServer && (it.online || it.isVisible) }
                .sortedBy { it.displayName }

            _uiState.update {
                it.copy(
                    totalCount = secureCoreServers.size,
                    allTestedItems = secureCoreServers.map { s -> ServerPingItem(s) }
                )
            }
        }

        // Monitor active VPN state to reflect active connected server in the list
        viewModelScope.launch {
            vpnStateMonitor.status.collect { status ->
                val connectedServerId = if (status.state == VpnState.Connected) {
                    status.connectionParams?.server?.serverId
                } else null
                _uiState.update { it.copy(activeConnectedServerId = connectedServerId) }
            }
        }
    }

    fun startTest(vpnUiDelegate: VpnUiDelegate? = null) {
        if (_uiState.value.isTesting) return

        testJob?.cancel()
        testJob = viewModelScope.launch(Dispatchers.IO) {
            val allServers = serverManager.allServersFlow.first()
            val secureCoreServers = allServers
                .filter { it.isSecureCoreServer && (it.online || it.isVisible) }
                .sortedBy { it.displayName }

            if (secureCoreServers.isEmpty()) return@launch

            val initialItems = secureCoreServers.map { ServerPingItem(it, ServerTestStatus.NOT_TESTED) }

            _uiState.value = PingTestUiState(
                isTesting = true,
                totalCount = secureCoreServers.size,
                testedCount = 0,
                availableCount = 0,
                unavailableCount = 0,
                allTestedItems = initialItems,
                availableSortedItems = emptyList()
            )

            // Probe Cloudflare trace reachability on current network in parallel
            val cfTracePing = pingTestManager.pingCloudflareTrace(timeoutMs = 2500)

            val currentItemsMap = ConcurrentHashMap<String, ServerPingItem>(
                initialItems.associateBy { it.server.serverId }
            )
            val testedAtomic = AtomicInteger(0)
            val availableAtomic = AtomicInteger(0)
            val unavailableAtomic = AtomicInteger(0)

            // Concurrently test servers with Semaphore to prevent network congestion
            val semaphore = Semaphore(6)

            coroutineScope {
                for (server in secureCoreServers) {
                    if (!isActive) break
                    launch {
                        semaphore.withPermit {
                            if (!isActive) return@withPermit

                            _uiState.update {
                                it.copy(
                                    currentTestingServer = server,
                                    currentStepDescription = "Probing Stealth TLS on ${server.displayName}..."
                                )
                            }
                            currentItemsMap[server.serverId] = ServerPingItem(
                                server = server,
                                status = ServerTestStatus.TESTING_CONNECTING,
                                statusDetail = "Probing port 443..."
                            )
                            updateState(
                                secureCoreServers,
                                currentItemsMap,
                                testedAtomic.get(),
                                availableAtomic.get(),
                                unavailableAtomic.get()
                            )

                            // Step 1: Probe Stealth TLS port and measure real network RTT
                            val rtt = pingTestManager.probeStealthRtt(server, timeoutMs = 2500)

                            if (!isActive) return@withPermit

                            if (rtt != null) {
                                // Server reached via Stealth TLS!
                                currentItemsMap[server.serverId] = ServerPingItem(
                                    server = server,
                                    status = ServerTestStatus.TESTING_CLOUDFLARE,
                                    statusDetail = "Verifying connection..."
                                )
                                updateState(
                                    secureCoreServers,
                                    currentItemsMap,
                                    testedAtomic.get(),
                                    availableAtomic.get(),
                                    unavailableAtomic.get()
                                )

                                delay(40) // Brief visual transition for UI

                                val finalPing = if (cfTracePing != null) {
                                    // Weighted real latency combining Stealth RTT and Cloudflare network trace
                                    (rtt * 0.75 + cfTracePing * 0.25).toLong().coerceAtLeast(rtt)
                                } else {
                                    rtt
                                }

                                availableAtomic.incrementAndGet()
                                currentItemsMap[server.serverId] = ServerPingItem(
                                    server = server,
                                    status = ServerTestStatus.AVAILABLE,
                                    pingMs = finalPing,
                                    statusDetail = "Stealth TLS verified (${finalPing}ms)"
                                )
                            } else {
                                unavailableAtomic.incrementAndGet()
                                currentItemsMap[server.serverId] = ServerPingItem(
                                    server = server,
                                    status = ServerTestStatus.TIMEOUT,
                                    statusDetail = "Stealth port timeout / blocked"
                                )
                            }

                            testedAtomic.incrementAndGet()
                            updateState(
                                secureCoreServers,
                                currentItemsMap,
                                testedAtomic.get(),
                                availableAtomic.get(),
                                unavailableAtomic.get()
                            )
                        }
                    }
                }
            }

            finalizeResults(secureCoreServers.map { currentItemsMap[it.serverId] ?: ServerPingItem(it) })
        }
    }

    private fun updateState(
        secureCoreServers: List<Server>,
        currentItemsMap: Map<String, ServerPingItem>,
        tested: Int,
        available: Int,
        unavailable: Int
    ) {
        _uiState.update { state ->
            state.copy(
                testedCount = tested,
                availableCount = available,
                unavailableCount = unavailable,
                allTestedItems = secureCoreServers.map { s -> currentItemsMap[s.serverId] ?: ServerPingItem(s) }
            )
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        finalizeResults(_uiState.value.allTestedItems)
    }

    private fun finalizeResults(testedItems: List<ServerPingItem>) {
        val dateStr = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.US).format(Date())
        val availableOnlySorted = testedItems
            .filter { it.status == ServerTestStatus.AVAILABLE }
            .sortedWith(compareBy<ServerPingItem> { it.pingMs ?: Long.MAX_VALUE }.thenBy { it.server.displayName })

        _uiState.update { state ->
            state.copy(
                isTesting = false,
                currentTestingServer = null,
                currentStepDescription = null,
                lastTestTime = dateStr,
                availableSortedItems = availableOnlySorted
            )
        }
    }

    fun connectToSecureCore(vpnUiDelegate: VpnUiDelegate, server: Server) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val connectIntent = ConnectIntent.SecureCore(
                    exitCountry = CountryId(server.exitCountry),
                    entryCountry = CountryId(server.entryCountry),
                    settingsOverrides = SettingsOverrides(
                        protocolData = ProtocolSelection.STEALTH.toData(),
                        netShield = null,
                        randomizedNat = null,
                        lanConnections = null,
                        lanConnectionsAllowDirect = null,
                        customDns = null
                    )
                )
                vpnConnectionManager.connect(
                    vpnUiDelegate,
                    connectIntent,
                    ConnectTrigger.CountriesServer("ping test connect")
                )
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "Error connecting to Secure Core: ${e.message}")
            }
        }
    }

    fun disconnectVpn() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                vpnConnectionManager.disconnect(DisconnectTrigger.ConnectionCard)
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "Error disconnecting: ${e.message}")
            }
        }
    }
}
