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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VpnPauseReceiver : BroadcastReceiver() {

    @Inject lateinit var vpnPauseManager: VpnPauseManager

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        ProtonLogger.logCustom(LogCategory.CONN, "VpnPauseReceiver received action: $action")

        when (action) {
            VpnPauseManager.ACTION_PAUSE_EXPIRED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        vpnPauseManager.onPauseExpired()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Initializing or accessing vpnPauseManager triggers restoreState() logic
                if (vpnPauseManager.isPaused()) {
                    ProtonLogger.logCustom(
                        LogCategory.CONN,
                        "Device reboot or app update occurred while VPN was paused. Restored pause state."
                    )
                }
            }
        }
    }
}
