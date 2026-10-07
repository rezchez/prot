/*
 * Copyright (c) 2022 Proton AG
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

package com.protonvpn.android.models.vpn.usecase

import com.protonvpn.android.appconfig.AppConfig
import com.protonvpn.android.di.Distinct
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.servers.Server
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@Distinct
class GetSmartProtocols @Inject constructor(
    val appConfig: AppConfig
) {
    suspend operator fun invoke(): List<ProtocolSelection> = appConfig.getSmartProtocols()
    fun observe(): Flow<List<ProtocolSelection>> = appConfig.appConfigFlow
        .map { it.smartProtocolConfig.getSmartProtocols() }
        .distinctUntilChanged()
}

typealias SmartProtocols = List<ProtocolSelection>

fun supportsProtocol(server: Server, protocol: ProtocolSelection, smartProtocols: SmartProtocols): Boolean =
    server.connectingDomains.any { supportsProtocol(it, protocol, smartProtocols) }

fun supportsProtocol(server: Server, vpnProtocol: VpnProtocol, smartProtocols: SmartProtocols): Boolean =
    if (vpnProtocol == VpnProtocol.Smart) {
        supportsProtocol(server, ProtocolSelection.SMART, smartProtocols)
    } else {
        ProtocolSelection.PROTOCOLS_FOR[vpnProtocol]
            ?.any { supportsProtocol(server, it, smartProtocols) } == true
    }

fun supportsProtocol(connectingDomain: ConnectingDomain, protocol: ProtocolSelection, smartProtocols: SmartProtocols) =
    if (protocol.vpn == VpnProtocol.Smart) {
        smartProtocols.any { connectingDomain.supportsRealProtocol(it) }
    } else {
        connectingDomain.supportsRealProtocol(protocol)
    }

private fun ConnectingDomain.supportsRealProtocol(protocol: ProtocolSelection) =
    getEntryIp(protocol) != null &&
            ((protocol.vpn !in arrayOf(VpnProtocol.WireGuard, VpnProtocol.ProTun)) || !publicKeyX25519.isNullOrBlank())
