/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import com.protonvpn.android.servers.Server

enum class ServerTestStatus {
    NOT_TESTED,
    TESTING,
    WORKING,
    FAILED
}

enum class PingTestTab {
    WORKING,
    ALL
}

data class ServerTestItem(
    val server: Server,
    val status: ServerTestStatus = ServerTestStatus.NOT_TESTED,
    val currentAttempt: Int = 0,
    val maxAttempts: Int = 1,
    val connectTimeMs: Long? = null,
    val errorMessage: String? = null
)

data class PingCountryItem(
    val countryCode: String,
    val countryName: String,
    val serverCount: Int
)

data class PingTestUiState(
    val isTesting: Boolean = false,
    val isPaused: Boolean = false,
    val currentTestingServer: Server? = null,
    val currentAttempt: Int = 1,
    val attemptsPerServer: Int = 1,
    val attemptsInputText: String = "1",
    val attemptsError: String? = null,
    val currentStepDescription: String? = null,
    val totalCount: Int = 0,
    val testedCount: Int = 0,
    val workingCount: Int = 0,
    val failedCount: Int = 0,
    val lastTestTime: String? = null,
    val allServers: List<ServerTestItem> = emptyList(),
    val workingServers: List<ServerTestItem> = emptyList(),
    val showConfirmDialog: Boolean = false,
    val hasActiveVpn: Boolean = false,
    val activeConnectedServerId: String? = null,
    val selectedTab: PingTestTab = PingTestTab.WORKING,
    val availableCountries: List<PingCountryItem> = emptyList(),
    val selectedCountryCodes: Set<String> = emptySet(),
    val showCountryFilterSheet: Boolean = false,
    val savedProfileServerIds: Set<String> = emptySet()
) {
    val progressPercent: Int
        get() = if (totalCount > 0) ((testedCount * 100) / totalCount).coerceIn(0, 100) else 0

    val estimatedDurationText: String
        get() {
            val estimatedSeconds = totalCount * attemptsPerServer * 10
            val minutes = estimatedSeconds / 60
            val seconds = estimatedSeconds % 60
            return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
        }

    val selectedCountriesSummary: String
        get() {
            return if (selectedCountryCodes.isEmpty() || selectedCountryCodes.size == availableCountries.size) {
                "All countries (${availableCountries.size})"
            } else {
                "${selectedCountryCodes.size} of ${availableCountries.size} countries ($totalCount servers)"
            }
        }
}
