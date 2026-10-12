/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import com.protonvpn.android.servers.Server

enum class ServerTestStatus {
    NOT_TESTED,
    TESTING_CONNECTING,
    TESTING_CLOUDFLARE,
    AVAILABLE,
    UNAVAILABLE,
    TIMEOUT,
    ERROR
}

data class ServerPingItem(
    val server: Server,
    val status: ServerTestStatus = ServerTestStatus.NOT_TESTED,
    val pingMs: Long? = null,
    val statusDetail: String? = null
)

data class PingTestUiState(
    val isTesting: Boolean = false,
    val currentTestingServer: Server? = null,
    val currentStepDescription: String? = null,
    val totalCount: Int = 0,
    val testedCount: Int = 0,
    val availableCount: Int = 0,
    val unavailableCount: Int = 0,
    val lastTestTime: String? = null,
    val allTestedItems: List<ServerPingItem> = emptyList(),
    val availableSortedItems: List<ServerPingItem> = emptyList(),
    val activeConnectedServerId: String? = null
) {
    val progressPercent: Int
        get() = if (totalCount > 0) ((testedCount * 100) / totalCount).coerceIn(0, 100) else 0
}
