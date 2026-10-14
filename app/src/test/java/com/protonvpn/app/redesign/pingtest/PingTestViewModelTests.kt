/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.app.redesign.pingtest

import android.content.SharedPreferences
import com.protonvpn.android.redesign.pingtest.PingTestViewModel
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.ServerManager2
import com.protonvpn.android.utils.SharedPreferencesProvider
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.pause.VpnPauseManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PingTestViewModelTests {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val serverManager = mockk<ServerManager2>(relaxed = true)
    private val vpnConnectionManager = mockk<VpnConnectionManager>(relaxed = true)
    private val vpnStateMonitor = mockk<VpnStateMonitor>(relaxed = true)
    private val vpnPauseManager = mockk<VpnPauseManager>(relaxed = true)
    private val prefsProvider = mockk<SharedPreferencesProvider>()
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val sharedPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)

    private val statusFlow = MutableStateFlow(
        VpnStateMonitor.Status(VpnState.Disabled, null)
    )

    private fun createMockServer(id: String, name: String, exitCountry: String = "US"): Server {
        val server = mockk<Server>(relaxed = true)
        every { server.serverId } returns id
        every { server.displayName } returns name
        every { server.serverName } returns name
        every { server.exitCountry } returns exitCountry
        every { server.online } returns true
        every { server.isVisible } returns true
        return server
    }

    private lateinit var viewModel: PingTestViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { prefsProvider.getPrefs(any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns sharedPrefsEditor
        every { sharedPrefs.getInt("attempts_per_server", 1) } returns 1
        every { sharedPrefsEditor.putInt(any(), any()) } returns sharedPrefsEditor

        val server1 = createMockServer("s2", "Beta Server")
        val server2 = createMockServer("s1", "Alpha Server")
        every { serverManager.allServersFlow } returns flowOf(listOf(server1, server2))
        every { vpnStateMonitor.status } returns statusFlow

        viewModel = PingTestViewModel(
            serverManager = serverManager,
            vpnConnectionManager = vpnConnectionManager,
            vpnStateMonitor = vpnStateMonitor,
            vpnPauseManager = vpnPauseManager,
            prefsProvider = prefsProvider
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads servers sorted alphabetically`() {
        val state = viewModel.uiState.value
        assertEquals(2, state.totalCount)
        assertEquals("Alpha Server", state.allTestedItems[0].server.displayName)
        assertEquals("Beta Server", state.allTestedItems[1].server.displayName)
        assertEquals(1, state.attemptsPerServer)
    }

    @Test
    fun `validates attempt input correctly`() {
        viewModel.onAttemptsInputChanged("3")
        assertEquals(3, viewModel.uiState.value.attemptsPerServer)
        assertNull(viewModel.uiState.value.attemptsError)

        viewModel.onAttemptsInputChanged("0")
        assertEquals("Must be between 1 and 10 attempts", viewModel.uiState.value.attemptsError)

        viewModel.onAttemptsInputChanged("15")
        assertEquals("Must be between 1 and 10 attempts", viewModel.uiState.value.attemptsError)
    }

    @Test
    fun `cancelTest resets testing state`() {
        viewModel.onRequestStartTest()
        viewModel.cancelTest()

        assertFalse(viewModel.uiState.value.isTesting)
        assertNull(viewModel.uiState.value.currentTestingServer)
    }
}
