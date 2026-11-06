/*
 * Copyright (c) 2021 Proton AG
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
package com.protonvpn.android.models.vpn

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.servers.Server
import com.protonvpn.android.servers.api.ConnectingDomain
import com.protonvpn.android.settings.data.LocalUserSettings
import com.protonvpn.android.settings.data.SplitTunnelingSettings
import com.protonvpn.android.utils.Constants.VPN_SERVER_IP
import com.protonvpn.android.utils.Constants.VPN_SERVER_IP_V6
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.models.vpn.usecase.toIPAddress
import inet.ipaddr.IPAddress
import me.proton.core.network.domain.session.SessionId
import me.proton.vpn.core.api.ConnectionMode
import me.proton.vpn.core.api.InitialConfig
import me.proton.vpn.core.api.InterfaceConfig
import me.proton.vpn.core.api.IpNetworkPrefix
import me.proton.vpn.core.api.Peer
import me.proton.vpn.core.api.SniStrategy
import me.proton.vpn.core.api.SplitTunnelAppsConfig
import me.proton.vpn.core.api.SplitTunnelMode
import java.net.InetAddress

class ConnectionParamsWireguard(
    connectIntent: AnyConnectIntent,
    server: Server,
    port: Int,
    connectingDomain: ConnectingDomain,
    entryIp: String?,
    transmission: TransmissionProtocol,
    ipv6SettingEnabled: Boolean,
) : ConnectionParams(
    connectIntent,
    server,
    connectingDomain,
    VpnProtocol.WireGuard,
    entryIp,
    port,
    transmission,
    ipv6SettingEnabled = ipv6SettingEnabled,
), java.io.Serializable {

    override val info get() = "${super.info} $transmissionProtocol port: $port"

    @Throws(IllegalStateException::class)
    suspend fun getTunnelConfig(
        context: Context,
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository,
        computeAllowedIPs: ComputeAllowedIPs,
        sniStrategy: SniStrategy = SniStrategy.Random,
    ): InitialConfig =
        getTunnelConfig(
            context.packageName,
            userSettings,
            sessionId,
            certificateRepository,
            computeAllowedIPs,
            sniStrategy,
        )

    @Throws(IllegalStateException::class)
    @VisibleForTesting
    suspend fun getTunnelConfig(
        myPackageName: String,
        userSettings: LocalUserSettings,
        sessionId: SessionId?,
        certificateRepository: CertificateRepository,
        computeAllowedIPs: ComputeAllowedIPs,
        sniStrategy: SniStrategy,
    ): InitialConfig {
        val entryIp = entryIp ?: requireNotNull(connectingDomain?.getEntryIp(protocolSelection))
        val allowedIps =
            if (connectIntent is AnyConnectIntent.GuestHole) {
                listOf("0.0.0.0/0".toIPAddress(), "::/0".toIPAddress())
            } else {
                computeAllowedIPs(userSettings)
            }
        val supportsIpv6 = enableIPv6 == true && server.isIPv6Supported
        val dnsServers = buildList {
            addAll(userSettings.customDns.effectiveDnsList)
            add(VPN_SERVER_IP)
            if (supportsIpv6) add(VPN_SERVER_IP_V6)
        }
        val splitTunnelAppsConfig = splitTunnelAppsConfig(
            myPackageName, userSettings.splitTunneling, userSettings.lanConnectionsAllowDirect
        )
        val protocol = when (protocolSelection?.transmission ?: TransmissionProtocol.UDP) {
            TransmissionProtocol.UDP -> me.proton.vpn.core.api.VpnProtocol.WireGuardUdp
            TransmissionProtocol.TCP -> me.proton.vpn.core.api.VpnProtocol.WireGuardTcp
            TransmissionProtocol.TLS -> me.proton.vpn.core.api.VpnProtocol.Stealth
        }
        val peer = Peer(
            InetAddress.getByName(entryIp),
            mapOf(protocol to listOf(requireNotNull(port))),
            requireNotNull(connectingDomain?.publicKeyX25519),
            priority = 0,
            id = server.serverId,
            exitLabel = null,
        )

        if (supportsIpv6) {
            ProtonLogger.logCustom(LogCategory.CONN, "WireGuard IPv4+6 tunnel")
        } else {
            ProtonLogger.logCustom(LogCategory.CONN, "WireGuard IPv4 tunnel")
        }
        val iface = InterfaceConfig(
            supportInTunnelIPv6 = supportsIpv6,
            customDns = dnsServers,
            routes = allowedIps.map { it.toIpNetworkPrefix() },
            splitTunnelAppsConfig = splitTunnelAppsConfig,
            mtu = 1420,
        )
        return InitialConfig(
            iface,
            listOf(peer),
            null,
            sniStrategy = sniStrategy,
            mode = ConnectionMode.NoLocalAgent(certificateRepository.getX25519Key(sessionId)),
        )
    }

    private fun splitTunnelAppsConfig(
        myPackageName: String,
        splitTunneling: SplitTunnelingSettings,
        allowDirectLanConnections: Boolean,
    ): SplitTunnelAppsConfig? {
        val configurator = SplitTunnelAppsRustConfigurator()
        applyAppsSplitTunneling(
            configurator, connectIntent, myPackageName, splitTunneling, allowDirectLanConnections
        )
        return configurator.buildConfig()
    }

    private class SplitTunnelAppsRustConfigurator : SplitTunnelAppsConfigurator {
        private var included = emptyList<String>()
        private var excluded = emptyList<String>()

        override fun includeApplications(packageNames: List<String>) {
            included += packageNames
        }

        override fun excludeApplications(packageNames: List<String>) {
            excluded += packageNames
        }

        fun buildConfig(): SplitTunnelAppsConfig? = when {
            included.isNotEmpty() -> SplitTunnelAppsConfig(SplitTunnelMode.Include, included)
            excluded.isNotEmpty() -> SplitTunnelAppsConfig(SplitTunnelMode.Exclude, excluded)
            else -> null
        }
    }

    private fun IPAddress.toIpNetworkPrefix() = IpNetworkPrefix(toInetAddress(), prefixLength)
}
