/*
 * Copyright (c) 2026. Proton AG
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

package com.protonvpn.android.ui

import android.content.Context
import com.bumptech.glide.Glide
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class GlideBitmapMemoryCleaner @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val mainScope: CoroutineScope,
    private val foregroundActivityTracker: ForegroundActivityTracker,
) {
    fun start() {
        mainScope.launch {
            delay(5.seconds)
            foregroundActivityTracker.foregroundBackgroundTransitionFlow
                .debounce(5.seconds)
                .onEach { (wasForeground, isForeground) ->
                    if (wasForeground && !isForeground) {
                        if (Glide.isInitialized()) {
                            Glide.get(appContext).clearMemory()
                        }
                    }
                }
                .launchIn(mainScope)
        }
    }
}
