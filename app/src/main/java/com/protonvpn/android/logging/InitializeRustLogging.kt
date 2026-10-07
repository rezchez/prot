/*
 * Copyright (c) 2026 Proton AG
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

package com.protonvpn.android.logging

import com.protonvpn.android.vpn.protun.VpnSdkLogger
import uniffi.protun.ClientLogger
import uniffi.protun.LogLevel
import uniffi.protun.initLogger
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InitializeRustLogging @Inject constructor(
    private val logger: VpnSdkLogger,
) {
    private val initialized = AtomicBoolean(false)

    // Should be called before any Rust code is executed, otherwise logs from Rust will not be
    // captured.
    fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            // If it was called already by another function (e.g. by SDK's VpnService), this call
            // will be a no-op.
            initLogger(LogLevel.INFO, object : ClientLogger {
                override fun log(level: LogLevel, message: String) {
                    logger.log(level, message)
                }
            })
        }
    }
}