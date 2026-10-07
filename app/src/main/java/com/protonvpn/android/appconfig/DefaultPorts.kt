/*
 * Copyright (c) 2019 Proton AG
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
package com.protonvpn.android.appconfig

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DefaultPorts(
    @SerialName(value = "UDP") val udpPorts: List<Int>,
    @SerialName(value = "TCP") val tcpPorts: List<Int> = emptyList(),
    @SerialName(value = "TLS") val tlsPorts: List<Int> = tcpPorts
)

@Serializable
data class DefaultPortsLegacyStorage(
    val udpPorts: List<Int>,
    val tcpPorts: List<Int> = emptyList(),
    val tlsPortsInternal: List<Int>? = null
)

fun DefaultPortsLegacyStorage.migrate() = DefaultPorts(
    udpPorts = udpPorts,
    tcpPorts = tcpPorts,
    tlsPorts = tlsPortsInternal ?: tcpPorts,
)