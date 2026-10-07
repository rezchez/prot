package com.protonvpn.android.appconfig

import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.vpn.ProtocolSelection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SmartProtocolConfig(
    @SerialName(value = "WireGuard") val wireguardEnabled: Boolean,
    @SerialName(value = "WireGuardTCP") val wireguardTcpEnabled: Boolean = true,
    @SerialName(value = "WireGuardTLS") val wireguardTlsEnabled: Boolean = true,
) {
    fun getSmartProtocols(): List<ProtocolSelection> =
        buildList {
            if (wireguardEnabled)
                add(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.UDP))
            if (wireguardTcpEnabled)
                add(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TCP))
            if (wireguardTlsEnabled)
                add(ProtocolSelection(VpnProtocol.WireGuard, TransmissionProtocol.TLS))
        }

    companion object {
        // TODO: or set defaults directly on the fields of the SmartProtocolConfig class?
        val default = SmartProtocolConfig(
            wireguardEnabled = true,
            wireguardTcpEnabled = true,
            wireguardTlsEnabled = true,
        )
    }
}

@Serializable
data class SmartProtocolConfigLegacyStorage(
    val wireguardEnabled: Boolean,
    val wireguardTcpEnabled: Boolean = true,
    val wireguardTlsEnabled: Boolean = true,
)

fun SmartProtocolConfigLegacyStorage.migrate() = SmartProtocolConfig(
    wireguardEnabled = wireguardEnabled,
    wireguardTcpEnabled = wireguardTcpEnabled,
    wireguardTlsEnabled = wireguardTlsEnabled,
)