/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.app.vpn.pause

import android.content.Context
import android.content.SharedPreferences
import com.protonvpn.android.notifications.NotificationHelper
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.ConnectIntentType
import com.protonvpn.android.redesign.recents.data.toAnyConnectIntent
import com.protonvpn.android.utils.SharedPreferencesProvider
import com.protonvpn.android.vpn.ConnectTrigger
import com.protonvpn.android.vpn.DisconnectTrigger
import com.protonvpn.android.vpn.VpnConnectionManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.pause.VpnPauseManager
import com.protonvpn.android.vpn.pause.VpnPauseState
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VpnPauseManagerTests {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val context = mockk<Context>(relaxed = true)
    private val prefsProvider = mockk<SharedPreferencesProvider>()
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true)
    private val sharedPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val vpnConnectionManager = mockk<VpnConnectionManager>(relaxed = true)
    private val vpnStateMonitor = mockk<VpnStateMonitor>(relaxed = true)
    private val notificationHelper = mockk<NotificationHelper>(relaxed = true)

    private val statusFlow = MutableStateFlow(
        VpnStateMonitor.Status(VpnState.Connected, null)
    )

    private lateinit var vpnPauseManager: VpnPauseManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { prefsProvider.getPrefs(any()) } returns sharedPrefs
        every { sharedPrefs.edit() } returns sharedPrefsEditor
        every { sharedPrefsEditor.putLong(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.putString(any(), any()) } returns sharedPrefsEditor
        every { sharedPrefsEditor.clear() } returns sharedPrefsEditor
        every { vpnStateMonitor.status } returns statusFlow

        vpnPauseManager = VpnPauseManager(
            context = context,
            prefsProvider = prefsProvider,
            vpnConnectionManager = vpnConnectionManager,
            vpnStateMonitor = vpnStateMonitor,
            notificationHelper = { notificationHelper },
            scope = testScope
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pauseVpn disconnects VPN and sets Paused state`() {
        val durationMs = 15 * 60 * 1000L
        vpnPauseManager.pauseVpn(durationMs, null)

        val slot = slot<DisconnectTrigger>()
        verify { vpnConnectionManager.disconnect(capture(slot)) }
        assertEquals("pause", slot.captured.description)

        assertTrue(vpnPauseManager.pauseState.value is VpnPauseState.Paused)
        val pausedState = vpnPauseManager.pauseState.value as VpnPauseState.Paused
        assertEquals(durationMs, pausedState.totalDurationMs)
    }

    @Test
    fun `cancelPause resets state to NotPaused`() {
        vpnPauseManager.pauseVpn(15 * 60 * 1000L, null)
        vpnPauseManager.cancelPause()

        assertEquals(VpnPauseState.NotPaused, vpnPauseManager.pauseState.value)
    }

    @Test
    fun `onPauseExpired triggers reconnect`() {
        val testIntentData = ConnectIntentData(ConnectIntentType.FASTEST)
        val jsonStr = kotlinx.serialization.json.Json.encodeToString(
            ConnectIntentData.serializer(),
            testIntentData
        )
        every { sharedPrefs.getString("pause_saved_intent", null) } returns jsonStr

        vpnPauseManager.onPauseExpired()

        assertEquals(VpnPauseState.NotPaused, vpnPauseManager.pauseState.value)
        val slot = slot<ConnectTrigger>()
        verify {
            vpnConnectionManager.connectInBackground(
                eq(testIntentData.toAnyConnectIntent()),
                capture(slot)
            )
        }
    }
}
