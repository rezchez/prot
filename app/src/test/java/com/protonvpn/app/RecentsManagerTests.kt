package com.protonvpn.app

import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.content.edit
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.ServerManager
import com.protonvpn.android.utils.Storage
import com.protonvpn.android.vpn.RecentsManager
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.VpnStateMonitor
import com.protonvpn.android.vpn.VpnStatusProviderUI
import com.protonvpn.mocks.createInMemoryServerManager
import com.protonvpn.test.shared.MockSharedPreference
import com.protonvpn.test.shared.TestDispatcherProvider
import com.protonvpn.test.shared.createServer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class RecentsManagerTests {

    private lateinit var serverManager: ServerManager
    private lateinit var storagePrefs: SharedPreferences
    private lateinit var testScope: TestScope

    @RelaxedMockK private lateinit var vpnStatusProviderUI: VpnStatusProviderUI

    private val vpnStatus = MutableStateFlow(VpnStateMonitor.Status(VpnState.Disabled, null))

    @get:Rule var rule = InstantTaskExecutorRule()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        storagePrefs = MockSharedPreference()
        Storage.setPreferences(storagePrefs)
        every { vpnStatusProviderUI.status } returns vpnStatus
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        serverManager = createInMemoryServerManager(testScope, TestDispatcherProvider(testDispatcher), emptyList())
    }

    private fun TestScope.addRecent(connectionParams: ConnectionParams) {
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, connectionParams)
        runCurrent()
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Disconnecting, connectionParams)
        runCurrent()
    }

    private fun mockedConnectionParams(
        country: String,
        serverName: String = country,
    ): ConnectionParams {
        val connectIntent = ConnectIntent.FastestInCountry(CountryId(country), emptySet())
        val server = createServer(exitCountry = country, serverName = serverName)
        return ConnectionParams(connectIntent, server, mockk(), mockk())
    }

    private fun createRecentsManager() =
        RecentsManager(testScope.backgroundScope, vpnStatusProviderUI, serverManager)

    @Test
    fun testAddingNewServerOnlyAfterConnectedState() = testScope.runTest {
        val manager = createRecentsManager()
        val connectionParams = mockedConnectionParams("Test")
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connecting, connectionParams)
        runCurrent()
        assertEquals(0, manager.getRecentCountries().size)
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, connectionParams)
        runCurrent()
        assertEquals(1, manager.getRecentCountries().size)
    }

    @Test
    fun testReconnectingDoesntCreateDuplicates() = testScope.runTest {
        val manager = createRecentsManager()
        val connectionParams = mockedConnectionParams("Test")
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, connectionParams)
        runCurrent()
        assertEquals(1, manager.getRecentCountries().size)
        vpnStatus.value = VpnStateMonitor.Status(VpnState.Connected, connectionParams)
        runCurrent()
        assertEquals(1, manager.getRecentCountries().size)
    }

    @Test
    fun testNewlyUsedRecentsMovedToFront() = testScope.runTest {
        val manager = createRecentsManager()
        val connectionParams = mockedConnectionParams("DE", "Test")
        addRecent(connectionParams)
        addRecent(mockedConnectionParams("CH", "Test2"))
        assertEquals("CH", manager.getRecentCountries()[0])
        addRecent(connectionParams)
        assertEquals("DE", manager.getRecentCountries()[0])
    }

    @Test
    fun testRecentServers() = testScope.runTest {
        val manager = createRecentsManager()
        addRecent(mockedConnectionParams("A", "A1"))
        addRecent(mockedConnectionParams("A", "A2"))
        addRecent(mockedConnectionParams("A", "A3"))
        addRecent(mockedConnectionParams("A", "A4"))
        addRecent(mockedConnectionParams("A", "A3"))
        addRecent(mockedConnectionParams("B", "B1"))

        assertEquals(listOf("A3", "A4", "A2"), manager.getRecentServers("A")?.map { it.serverName })
        assertEquals(listOf("B1"), manager.getRecentServers("B")?.map { it.serverName })
    }

    @Test
    fun testRecentManagerStateRestore() = testScope.runTest {
        val jsonTvRecentsManager = """
            {"recentConnections":[],"recentCountries":["AR"],"recentServers":{"AR":[{"city":"Buenos Aires","connectingDomains":[{"entryDomain":"node-ar-05.protonvpn.net","entryIp":"103.106.58.163","id":"iMLBZnOJin6LJALVoMtKRIvOY0pAg4oEeQHyIYu8-x4Y2VNkWB5Sw1XxaFxWONuBJT_lG0kFpCIbmZNAFXeqmQ\u003d\u003d","isOnline":true,"label":"4","publicKeyX25519":"0qOv5inT/bLBy2/vjMfwvhWTTN+qA2c/vgKMCqFYd1g\u003d"}],"entryCountry":"AR","entryLocation":{"latitude":-34.6,"longitude":-58.38},"exitLocation":{"latitude":-34.6,"longitude":-58.38},"features":12,"isVisible":true,"load":34.0,"online":true,"rawExitCountry":"AR","rawIsOnline":true,"score":1.7698542401737474,"serverId":"0ND8V1ELGsFFuubntY39AZjopvH_WYnaDwtHB1nIbyoHcFx2A8T7aq3nAcR__RMF69NeeZ3FdabbZuC5v2pcEg\u003d\u003d","serverName":"AR#37","serverNumber":37,"statusReference":{"cost":1,"index":17241,"penalty":1.0},"tier":2}]}}
        """.trimIndent()
        storagePrefs.edit {
            putString("com.protonvpn.android.vpn.RecentsManager", jsonTvRecentsManager)
        }
        val recentServer = createServer(
            serverId = "0ND8V1ELGsFFuubntY39AZjopvH_WYnaDwtHB1nIbyoHcFx2A8T7aq3nAcR__RMF69NeeZ3FdabbZuC5v2pcEg\u003d\u003d",
            serverName ="AR#37",
            exitCountry = "AR",
        )
        serverManager.setServers(listOf(recentServer), null)
        val manager = createRecentsManager()
        runCurrent()
        val recentServers = manager.getRecentServers("AR")
        assertEquals(listOf("AR"), manager.getRecentCountries())
        assertNotNull(recentServers)
        assertEquals(1, recentServers.size)
        assertEquals("AR#37", recentServers.first().serverName)
    }
}
