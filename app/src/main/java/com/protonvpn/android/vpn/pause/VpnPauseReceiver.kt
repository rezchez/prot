/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.vpn.pause

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VpnPauseReceiver : BroadcastReceiver() {

    @Inject lateinit var vpnPauseManager: VpnPauseManager

    override fun onReceive(context: Context?, intent: Intent?) {
        ProtonLogger.logCustom(LogCategory.CONN, "VpnPauseReceiver received intent: ${intent?.action}")
        if (intent?.action == ACTION_PAUSE_EXPIRED) {
            vpnPauseManager.onPauseExpired()
        }
    }

    companion object {
        const val ACTION_PAUSE_EXPIRED = "com.protonvpn.android.ACTION_PAUSE_EXPIRED"
    }
}
