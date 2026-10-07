/*
 * Copyright (c) 2022 Proton AG
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
data class RatingConfig(
    @SerialName(value = "EligiblePlans") val eligiblePlans: List<String>,
    @SerialName(value = "SuccessConnections") val successfulConnectionCount: Int,
    @SerialName(value = "DaysLastReviewPassed") val daysSinceLastRatingCount: Int,
    @SerialName(value = "DaysConnected") val daysConnectedCount: Int,
    @SerialName(value = "DaysFromFirstConnection") val daysFromFirstConnectionCount: Int
) {
    companion object {
        // TODO: or set defaults directly on the fields of the RatingConfig class?
        val default = RatingConfig(
            eligiblePlans = listOf("plus"),
            successfulConnectionCount = 3,
            daysSinceLastRatingCount = 3,
            daysConnectedCount = 3,
            daysFromFirstConnectionCount = 3
        )
    }
}

@Serializable
data class RatingConfigLegacyStorage(
    val eligiblePlans: List<String>,
    val successfulConnectionCount: Int,
    val daysSinceLastRatingCount: Int,
    val daysConnectedCount: Int,
    val daysFromFirstConnectionCount: Int
)

fun RatingConfigLegacyStorage.migrate() = RatingConfig(
    eligiblePlans = eligiblePlans,
    successfulConnectionCount = successfulConnectionCount,
    daysSinceLastRatingCount = daysSinceLastRatingCount,
    daysConnectedCount = daysConnectedCount,
    daysFromFirstConnectionCount = daysFromFirstConnectionCount,
)