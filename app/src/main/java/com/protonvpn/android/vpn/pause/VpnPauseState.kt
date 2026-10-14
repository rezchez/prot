/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.vpn.pause

import com.protonvpn.android.redesign.recents.data.ConnectIntentData

sealed interface VpnPauseState {
    data object NotPaused : VpnPauseState

    data class Paused(
        val deadlineMs: Long,
        val remainingMs: Long,
        val totalDurationMs: Long,
        val savedConnectIntentData: ConnectIntentData?
    ) : VpnPauseState
}
