/*
 * Copyright (c) 2025. Proton AG
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

package com.protonvpn.android.vpn.autoconnect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AutoConnectBootReceiver : BroadcastReceiver() {

    @Inject lateinit var workManager: dagger.Lazy<WorkManager>

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // Firestick and some other TV devices put aggressive time constraints on this broadcast
        // receiver so we're making it as lightweight as possible:
        // - running in dedicated, light process
        // - not doing any actual work but delegating it (with delay) to work manager.
        // NOTE: on firestick HD it still can take 30s for the broadcast receiver to even start so
        // connection will be established with a significant delay.
        AutoConnectOnBootWorker.enqueue(workManager.get())
    }
}
