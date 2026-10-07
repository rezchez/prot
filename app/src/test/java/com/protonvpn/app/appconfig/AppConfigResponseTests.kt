/*
 * Copyright (c) 2026. Proton AG
 *
 *  This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonVPN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonVPN.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.protonvpn.app.appconfig

import androidx.core.content.edit
import com.protonvpn.android.appconfig.AppConfigResponse
import com.protonvpn.android.appconfig.AppConfigResponseLegacyStorage
import com.protonvpn.android.appconfig.DefaultPorts
import com.protonvpn.android.appconfig.migrate
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import me.proton.core.util.android.sharedpreferences.put
import me.proton.core.util.kotlin.ProtonCoreConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNotNull

class AppConfigResponseTests {

    private val fullConfigJson = """
        {
          "Code": 1000,
          "DefaultPorts": {
            "OpenVPN": {
              "UDP": [
                80,
                51820,
                4569,
                1194,
                5060
              ],
              "TCP": [
                443,
                7770,
                8443
              ]
            },
            "WireGuard": {
              "UDP": [
                443,
                88,
                1224,
                51820,
                500,
                4500
              ],
              "TCP": [
                443
              ],
              "TLS": [
                443
              ]
            }
          },
          "HolesIPs": [
            "62.112.9.168",
            "104.245.144.186"
          ],
          "ServerRefreshInterval": 40,
          "FeatureFlags": {
            "NetShield": true,
            "GuestHoles": true,
            "ServerRefresh": true,
            "StreamingServicesLogos": true,
            "PortForwarding": false,
            "ModerateNAT": true,
            "SafeMode": false,
            "StartConnectOnBoot": true,
            "PollNotificationAPI": true,
            "VpnAccelerator": true,
            "SmartReconnect": true,
            "PromoCode": false,
            "WireGuardTls": true,
            "Telemetry": true,
            "NetShieldStats": true,
            "BusinessEvents": false,
            "ShowNewFreePlan": false
          },
          "SmartProtocol": {
            "WireGuard": true,
            "WireGuardTCP": true,
            "WireGuardTLS": true,
            "OpenVPN": false,
            "OpenVPNTCP": false,
            "IKEv2": false
          },
          "RatingSettings": {
            "EligiblePlans": [
              "vpn2022",
              "vpn2024",
              "bundle2022",
              "family2022",
              "duo2024",
              "visionary2022",
              "vpnpass2023"
            ],
            "SuccessConnections": 2,
            "DaysLastReviewPassed": 100,
            "DaysConnected": 3,
            "DaysFromFirstConnection": 0
          },
          "ChangeServerAttemptLimit": 4,
          "ChangeServerShortDelayInSeconds": 45,
          "ChangeServerLongDelayInSeconds": 600
        }
    """.trimIndent()

    private val JSON = ProtonCoreConfig.defaultJson

    private val legacyStorageJson = """
        {"changeServerAttemptLimit":4,"changeServerLongDelayInSeconds":600,"changeServerShortDelayInSeconds":45,"defaultPortsConfig":{"wireguardPorts":{"tcpPorts":[443],"tlsPortsInternal":[443],"udpPorts":[443,88,1224,51820,500,4500]}},"featureFlags":{"guestHoleEnabled":false,"maintenanceTrackerEnabled":true,"pollApiNotifications":true,"streamingServicesLogos":true,"wireguardTlsEnabled":true},"largeMetricsSamplingMultiplier":100,"logicalsRefreshBackgroundDelayMinutes":2880,"logicalsRefreshForegroundDelayMinutes":180,"ratingConfig":{"daysConnectedCount":3,"daysFromFirstConnectionCount":0,"daysSinceLastRatingCount":100,"eligiblePlans":["vpn2022","vpn2024","bundle2022","family2022","duo2024","visionary2022","vpnpass2023"],"successfulConnectionCount":2},"smartProtocolConfig":{"wireguardEnabled":true,"wireguardTcpEnabled":true,"wireguardTlsEnabled":true},"underMaintenanceDetectionDelay":40}
    """.trimIndent()

    @Test
    fun `deserialize with defaults`() {
        val json = "{}"
        val config = JSON.decodeFromString<AppConfigResponse>(json)
        val defaultConfig = AppConfigResponse()
        assertEquals(defaultConfig, config)
    }

    @Test
    fun `deserialize real config`() {
        val config = JSON.decodeFromString<AppConfigResponse>(fullConfigJson)
        assertEquals(listOf(443), config.defaultPortsConfig.getWireguardPorts().tcpPorts)
        assertTrue(config.smartProtocolConfig.wireguardEnabled)
        assertEquals(4, config.changeServerAttemptLimit)
    }

    @Test
    fun `migrate from legacy storage`() {
        val prefs = MockSharedPreference()
        prefs.edit {
            put("com.protonvpn.android.appconfig.AppConfigResponse", legacyStorageJson)
        }
        Storage.setPreferences(prefs)
        val configLegacy =
            Storage.load(AppConfigResponse::class.java, AppConfigResponseLegacyStorage.serializer())
        val config = configLegacy?.migrate()
        assertNotNull(config)
        assertEquals(4, config.changeServerAttemptLimit)
    }

    @Test
    fun `default ports deserialize TLS defaults`() {
        val jsonTcp = """ { "UDP": [1, 2, 3], "TCP": [10, 11] }"""
        val jsonNoTcp = """{ "UDP": [1, 2, 3] }"""
        assertEquals(
            JSON.decodeFromString<DefaultPorts>(jsonTcp).tlsPorts,
            listOf(10, 11)
        )
        assertEquals(
            JSON.decodeFromString<DefaultPorts>(jsonNoTcp).tlsPorts,
            emptyList<Int>()
        )
    }
}