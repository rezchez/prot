/*
 * Copyright (c) 2026. Proton AG
 *
 * This file is part of ProtonVPN.
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

package com.protonvpn.app.models.vpn

import androidx.core.content.edit
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.vpn.ConnectIntent
import com.protonvpn.android.utils.Storage
import com.protonvpn.test.shared.MockSharedPreference
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

class ConnectionParamsStorageTests {

    private val JsonConnectionParams = """
        {"connectIntentData":{"connectIntentType":"FASTEST","exitCountry":"AR","features":[]},"connectingDomain":{"entryDomain":"node-ar-05.protonvpn.net","entryIp":"103.106.58.163","id":"iMLBZnOJin6LJALVoMtKRIvOY0pAg4oEeQHyIYu8-x4Y2VNkWB5Sw1XxaFxWONuBJT_lG0kFpCIbmZNAFXeqmQ\u003d\u003d","isOnline":true,"label":"4","publicKeyX25519":"0qOv5inT/bLBy2/vjMfwvhWTTN+qA2c/vgKMCqFYd1g\u003d"},"enableIPv6":false,"entryIp":"103.106.58.163","port":443,"protocol":"WireGuard","server":{"city":"Buenos Aires","connectingDomains":[{"entryDomain":"node-ar-05.protonvpn.net","entryIp":"103.106.58.163","id":"iMLBZnOJin6LJALVoMtKRIvOY0pAg4oEeQHyIYu8-x4Y2VNkWB5Sw1XxaFxWONuBJT_lG0kFpCIbmZNAFXeqmQ\u003d\u003d","isOnline":true,"label":"4","publicKeyX25519":"0qOv5inT/bLBy2/vjMfwvhWTTN+qA2c/vgKMCqFYd1g\u003d"}],"entryCountry":"AR","entryLocation":{"latitude":-34.6,"longitude":-58.38},"exitLocation":{"latitude":-34.6,"longitude":-58.38},"features":12,"isVisible":true,"load":34.0,"online":true,"rawExitCountry":"AR","rawIsOnline":true,"score":1.7698542401737474,"serverId":"0ND8V1ELGsFFuubntY39AZjopvH_WYnaDwtHB1nIbyoHcFx2A8T7aq3nAcR__RMF69NeeZ3FdabbZuC5v2pcEg\u003d\u003d","serverName":"AR#37","serverNumber":37,"statusReference":{"cost":1,"index":17241,"penalty":1.0},"tier":2},"transmissionProtocol":"UDP","uuid":"0d5f553e-6cf6-4a65-9729-253897da2123"}
    """.trimIndent()

    @Before
    fun setup() {
        val storagePrefs = MockSharedPreference()
        Storage.setPreferences(storagePrefs)
        storagePrefs.edit {
            putString("com.protonvpn.android.models.vpn.ConnectionParams", JsonConnectionParams)
        }
    }

    @Test
    fun `read current connection ConnectIntent from persistent store`() {
        val connectIntent = ConnectionParams.readIntentFromStore(
            UUID.fromString("0d5f553e-6cf6-4a65-9729-253897da2123")
        )
        assertEquals(
            ConnectIntent.FastestInCountry(CountryId("AR"), emptySet()),
            connectIntent
        )
    }
}