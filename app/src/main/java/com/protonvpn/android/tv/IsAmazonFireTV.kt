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

package com.protonvpn.android.tv

import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

private const val AMAZON_FEATURE_FIRE_TV = "amazon.hardware.fire_tv"

@Reusable
class IsAmazonFireTV @VisibleForTesting constructor(
    private val value: () -> Boolean,
) {
    @Inject constructor(
        @ApplicationContext appContext: Context
    ) : this({ appContext.packageManager.hasSystemFeature(AMAZON_FEATURE_FIRE_TV) })

    operator fun invoke(): Boolean = value()
}