/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import android.content.Context
import android.os.SystemClock
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileEntity
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.ConnectIntentType
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.redesign.vpn.ServerFeature
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.ui.vpn.VpnBackgroundUiDelegate
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "ping_test_prefs"
private const val KEY_ATTEMPTS = "key_connection_attempts"
private const val DEFAULT_ATTEMPTS = 1
private const val MAX_ATTEMPTS = 10
private const val CONNECT_TIMEOUT_MS = 15000L
private const val TEARDOWN_TIMEOUT_MS = 8000L

@Singleton
class PingTestManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverManager: ServerManager2,
    private val vpnConnectionManager: VpnConnectionManager,
    private val vpnStateMonitor: VpnStateMonitor,
    private val vpnBackgroundUiDelegate: VpnBackgroundUiDelegate,
    private val profilesDao: ProfilesDao,
    private val currentUser: CurrentUser,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var testJob: Job? = null

    private val _state = MutableStateFlow(
        PingTestUiState(
            attemptsPerServer = prefs.getInt(KEY_ATTEMPTS, DEFAULT_ATTEMPTS).coerceIn(1, MAX_ATTEMPTS),
            attemptsInputText = prefs.getInt(KEY_ATTEMPTS, DEFAULT_ATTEMPTS).coerceIn(1, MAX_ATTEMPTS).toString()
        )
    )
    val state: StateFlow<PingTestUiState> = _state.asStateFlow()

    init {
        // Load initial server list, build available countries list, and observe active connection
        scope.launch {
            serverManager.allServersFlow.collect { servers ->
                val validServers = servers.filter { it.online || it.isVisible }
                val countries = validServers.groupBy { it.exitCountry }
                    .map { (countryCode, sList) ->
                        val countryName = Locale("", countryCode).displayCountry.ifBlank { countryCode }
                        PingCountryItem(
                            countryCode = countryCode,
                            countryName = countryName,
                            serverCount = sList.size
                        )
                    }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.countryName })

                val selectedCodes = _state.value.selectedCountryCodes
                val filtered = if (selectedCodes.isEmpty()) {
                    validServers
                } else {
                    validServers.filter { selectedCodes.contains(it.exitCountry) }
                }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

                _state.update {
                    it.copy(
                        availableCountries = countries,
                        totalCount = filtered.size,
                        allServers = filtered.map { s -> ServerTestItem(s) }
                    )
                }
            }
        }

        // Track profile additions
        scope.launch {
            currentUser.vpnUserFlow.collect { vpnUser ->
                if (vpnUser != null) {
                    profilesDao.getProfiles(vpnUser.userId).collect { profiles ->
                        val serverIds = profiles.mapNotNull { p ->
                            when (val intent = p.connectIntent) {
                                is ConnectIntent.Server -> intent.serverId
                                else -> null
                            }
                        }.toSet()
                        _state.update { it.copy(savedProfileServerIds = serverIds) }
                    }
                }
            }
        }

        scope.launch {
            vpnStateMonitor.status.collect { status ->
                val connectedServerId = if (status.state == VpnState.Connected) {
                    status.connectionParams?.server?.serverId
                } else null
                val hasActive = status.state == VpnState.Connected || status.state.isEstablishingConnection
                _state.update {
                    it.copy(
                        activeConnectedServerId = connectedServerId,
                        hasActiveVpn = hasActive
                    )
                }
            }
        }
    }

    fun setAttemptsInput(text: String) {
        val trimmed = text.trim()
        val parsed = trimmed.toIntOrNull()
        val error = when {
            trimmed.isEmpty() -> "Please enter a value"
            parsed == null || parsed < 1 || parsed > MAX_ATTEMPTS -> "Enter a number between 1 and $MAX_ATTEMPTS"
            else -> null
        }
        _state.update {
            it.copy(
                attemptsInputText = text,
                attemptsError = error,
                attemptsPerServer = if (error == null && parsed != null) {
                    prefs.edit().putInt(KEY_ATTEMPTS, parsed).apply()
                    parsed
                } else {
                    it.attemptsPerServer
                }
            )
        }
    }

    fun setSelectedTab(tab: PingTestTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun showConfirmDialog(show: Boolean) {
        _state.update { it.copy(showConfirmDialog = show) }
    }

    fun showCountryFilterSheet(show: Boolean) {
        _state.update { it.copy(showCountryFilterSheet = show) }
    }

    fun toggleCountrySelection(countryCode: String) {
        val current = _state.value.selectedCountryCodes.toMutableSet()
        if (current.contains(countryCode)) {
            current.remove(countryCode)
        } else {
            current.add(countryCode)
        }
        setCountrySelection(current)
    }

    fun selectAllCountries() {
        setCountrySelection(emptySet())
    }

    fun setCountrySelection(selectedCodes: Set<String>) {
        scope.launch {
            val allAvailable = serverManager.allServersFlow.first()
                .filter { it.online || it.isVisible }
            val filtered = if (selectedCodes.isEmpty()) {
                allAvailable
            } else {
                allAvailable.filter { selectedCodes.contains(it.exitCountry) }
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

            _state.update {
                it.copy(
                    selectedCountryCodes = selectedCodes,
                    totalCount = filtered.size,
                    allServers = filtered.map { s -> ServerTestItem(s) }
                )
            }
        }
    }

    fun pauseTest() {
        if (_state.value.isTesting && !_state.value.isPaused) {
            _state.update { it.copy(isPaused = true, currentStepDescription = "Test paused") }
        }
    }

    fun resumeTest() {
        if (_state.value.isTesting && _state.value.isPaused) {
            _state.update { it.copy(isPaused = false, currentStepDescription = "Resuming test...") }
        }
    }

    suspend fun addServerToProfile(server: Server): Boolean {
        val user = currentUser.vpnUser() ?: return false
        val now = System.currentTimeMillis()
        val profileEntity = ProfileEntity(
            userId = user.userId,
            name = server.displayName,
            color = ProfileColor.Color1,
            icon = ProfileIcon.Icon1,
            autoOpenText = "",
            autoOpenEnabled = false,
            autoOpenUrlPrivately = false,
            createdAt = now,
            lastConnectedAt = null,
            isUserCreated = true,
            connectIntentData = ConnectIntentData(
                connectIntentType = ConnectIntentType.SPECIFIC_SERVER,
                exitCountry = server.exitCountry,
                serverId = server.serverId,
                features = server.serverFeatures
            )
        )
        profilesDao.upsert(profileEntity)
        _state.update {
            it.copy(savedProfileServerIds = it.savedProfileServerIds + server.serverId)
        }
        return true
    }

    @Synchronized
    fun startTest() {
        if (_state.value.isTesting) return

        showConfirmDialog(false)
        testJob?.cancel()

        testJob = scope.launch {
            runSequentialTest()
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        scope.launch {
            try {
                vpnConnectionManager.disconnectAndWait(DisconnectTrigger.Test("server_test_cancel"))
            } catch (_: Exception) {
                vpnConnectionManager.disconnect(DisconnectTrigger.Test("server_test_cancel"))
            }
            withTimeoutOrNull(TEARDOWN_TIMEOUT_MS) {
                vpnStateMonitor.status.first { it.state == VpnState.Disabled }
            }
            finalizeTest()
        }
    }

    private suspend fun runSequentialTest() {
        val selectedCodes = _state.value.selectedCountryCodes
        val allServers = serverManager.allServersFlow.first()
            .filter { it.online || it.isVisible }
            .let { list ->
                if (selectedCodes.isEmpty()) list
                else list.filter { selectedCodes.contains(it.exitCountry) }
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

        if (allServers.isEmpty()) {
            _state.update { it.copy(isTesting = false, isPaused = false) }
            return
        }

        // Check if VPN is connected before testing and cleanly disconnect
        if (vpnStateMonitor.status.value.state != VpnState.Disabled) {
            _state.update { it.copy(currentStepDescription = "Disconnecting previous VPN session...") }
            try {
                vpnConnectionManager.disconnectAndWait(DisconnectTrigger.Test("server_test_prep"))
            } catch (_: Exception) {
                vpnConnectionManager.disconnect(DisconnectTrigger.Test("server_test_prep"))
            }
            withTimeoutOrNull(TEARDOWN_TIMEOUT_MS) {
                vpnStateMonitor.status.first { it.state == VpnState.Disabled }
            }
        }

        val initialItems = allServers.map { ServerTestItem(it, ServerTestStatus.NOT_TESTED) }
        val attempts = _state.value.attemptsPerServer

        _state.update {
            it.copy(
                isTesting = true,
                isPaused = false,
                totalCount = allServers.size,
                testedCount = 0,
                workingCount = 0,
                failedCount = 0,
                allServers = initialItems,
                workingServers = emptyList(),
                currentTestingServer = null,
                currentStepDescription = null
            )
        }

        val allResultsMap = initialItems.associateBy { it.server.serverId }.toMutableMap()
        val workingMap = mutableMapOf<String, ServerTestItem>()
        var testedCount = 0
        var workingCount = 0
        var failedCount = 0

        for (server in allServers) {
            if (!scope.isActive) break

            // Check if paused before starting server
            while (_state.value.isPaused && _state.value.isTesting) {
                delay(300)
            }
            if (!scope.isActive || !_state.value.isTesting) break

            var serverSuccess = false
            var connectDuration: Long? = null
            var lastAttempt = 0

            for (attempt in 1..attempts) {
                if (!scope.isActive) break

                // Check if paused before attempt
                while (_state.value.isPaused && _state.value.isTesting) {
                    delay(300)
                }
                if (!scope.isActive || !_state.value.isTesting) break

                lastAttempt = attempt

                _state.update {
                    it.copy(
                        currentTestingServer = server,
                        currentAttempt = attempt,
                        currentStepDescription = "Testing ${server.displayName} (Attempt $attempt/$attempts)..."
                    )
                }

                allResultsMap[server.serverId] = ServerTestItem(
                    server = server,
                    status = ServerTestStatus.TESTING,
                    currentAttempt = attempt,
                    maxAttempts = attempts
                )
                updateProgress(allServers, allResultsMap, workingMap, testedCount, workingCount, failedCount)

                val connectIntent = server.toConnectIntent()

                val startTime = SystemClock.elapsedRealtime()

                vpnConnectionManager.connect(
                    vpnBackgroundUiDelegate,
                    connectIntent,
                    ConnectTrigger.Test("server_test_${server.serverId}_$attempt")
                )

                // Authoritative check: VpnState.Connected AND matching server
                val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    vpnStateMonitor.status.first { status ->
                        status.state == VpnState.Connected &&
                            status.connectionParams?.server?.serverId == server.serverId
                    }
                } != null

                val duration = SystemClock.elapsedRealtime() - startTime

                if (connected && scope.isActive) {
                    serverSuccess = true
                    connectDuration = duration
                }

                // Mandatory clean disconnect after each attempt/test (Requirement 2.4)
                _state.update {
                    it.copy(currentStepDescription = "Disconnecting ${server.displayName}...")
                }

                try {
                    vpnConnectionManager.disconnectAndWait(DisconnectTrigger.Test("server_test_teardown"))
                } catch (_: Exception) {
                    vpnConnectionManager.disconnect(DisconnectTrigger.Test("server_test_teardown"))
                }

                withTimeoutOrNull(TEARDOWN_TIMEOUT_MS) {
                    vpnStateMonitor.status.first { it.state == VpnState.Disabled }
                }

                // Stop further attempts on first success
                if (serverSuccess) {
                    break
                }
            }

            if (!scope.isActive) break

            testedCount++
            if (serverSuccess) {
                workingCount++
                val item = ServerTestItem(
                    server = server,
                    status = ServerTestStatus.WORKING,
                    currentAttempt = lastAttempt,
                    maxAttempts = attempts,
                    connectTimeMs = connectDuration
                )
                allResultsMap[server.serverId] = item
                workingMap[server.serverId] = item
            } else {
                failedCount++
                val item = ServerTestItem(
                    server = server,
                    status = ServerTestStatus.FAILED,
                    currentAttempt = lastAttempt,
                    maxAttempts = attempts,
                    errorMessage = "Failed to establish VPN connection"
                )
                allResultsMap[server.serverId] = item
            }

            updateProgress(allServers, allResultsMap, workingMap, testedCount, workingCount, failedCount)
        }

        finalizeTest()
    }

    private fun updateProgress(
        allServers: List<Server>,
        allResultsMap: Map<String, ServerTestItem>,
        workingMap: Map<String, ServerTestItem>,
        tested: Int,
        working: Int,
        failed: Int
    ) {
        _state.update {
            it.copy(
                testedCount = tested,
                workingCount = working,
                failedCount = failed,
                allServers = allServers.map { s -> allResultsMap[s.serverId] ?: ServerTestItem(s) },
                workingServers = workingMap.values.sortedWith(
                    compareBy<ServerTestItem> { item -> item.connectTimeMs ?: Long.MAX_VALUE }
                        .thenBy { item -> item.server.displayName }
                )
            )
        }
    }

    private fun finalizeTest() {
        val dateStr = SimpleDateFormat("dd MMM yyyy - HH:mm", Locale.US).format(Date())
        _state.update {
            it.copy(
                isTesting = false,
                isPaused = false,
                currentTestingServer = null,
                currentStepDescription = null,
                lastTestTime = dateStr
            )
        }
    }

    fun connectToServer(vpnUiDelegate: VpnUiDelegate, server: Server) {
        scope.launch(Dispatchers.Main) {
            try {
                val connectIntent = server.toConnectIntent()
                vpnConnectionManager.connect(
                    vpnUiDelegate,
                    connectIntent,
                    ConnectTrigger.CountriesServer("tested_server_connect")
                )
            } catch (e: Exception) {
                ProtonLogger.logCustom(LogCategory.CONN, "Error connecting to tested server: ${e.message}")
            }
        }
    }
}

private fun Server.toConnectIntent(): ConnectIntent {
    val gwName = gatewayName
    return when {
        isSecureCoreServer -> ConnectIntent.SecureCore(
            exitCountry = CountryId(exitCountry),
            entryCountry = CountryId(entryCountry)
        )
        gwName != null -> ConnectIntent.Gateway(
            gatewayName = gwName,
            serverId = serverId
        )
        else -> ConnectIntent.Server(
            serverId = serverId,
            exitCountry = CountryId(exitCountry),
            features = serverFeatures
        )
    }
}

private val Server.serverFeatures: Set<ServerFeature>
    get() = buildSet {
        if (isP2pServer) add(ServerFeature.P2P)
        if (isTor) add(ServerFeature.Tor)
    }
