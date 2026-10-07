/*
 * Copyright (c) 2019 Proton AG
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

import com.protonvpn.android.utils.Constants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

val DEFAULT_SERVER_LIST_REFRESH_FOREGROUND_MINUTES = TimeUnit.HOURS.toMinutes(3)
val DEFAULT_SERVER_LIST_REFRESH_BACKGROUND_MINUTES = TimeUnit.DAYS.toMinutes(2)
const val DEFAULT_ATTEMPT_COUNT = 4
const val DEFAULT_CHANGE_SHORT_DELAY_SECONDS = 90
const val DEFAULT_CHANGE_LONG_DELAY_SECONDS = 1200

// Large metrics will use this value multiplier with p = 1 / DEFAULT_LARGE_METRICS_SAMPLING_FACTOR
const val DEFAULT_LARGE_METRICS_SAMPLING_MULTIPLIER = 100

@Serializable
data class AppConfigResponse(
    @SerialName(value = "ServerRefreshInterval")
    val underMaintenanceDetectionDelay: Long = Constants.DEFAULT_MAINTENANCE_CHECK_MINUTES,
    @SerialName(value = "LogicalsRefreshIntervalForegroundMinutes")
    val logicalsRefreshForegroundDelayMinutes: Long = DEFAULT_SERVER_LIST_REFRESH_FOREGROUND_MINUTES,
    @SerialName(value = "LogicalsRefreshIntervalBackgroundMinutes")
    val logicalsRefreshBackgroundDelayMinutes: Long = DEFAULT_SERVER_LIST_REFRESH_BACKGROUND_MINUTES,
    @SerialName(value = "ChangeServerAttemptLimit")
    val changeServerAttemptLimit: Int = DEFAULT_ATTEMPT_COUNT,
    @SerialName(value = "ChangeServerShortDelayInSeconds")
    val changeServerShortDelayInSeconds: Int = DEFAULT_CHANGE_SHORT_DELAY_SECONDS,
    @SerialName(value = "ChangeServerLongDelayInSeconds")
    val changeServerLongDelayInSeconds: Int = DEFAULT_CHANGE_LONG_DELAY_SECONDS,
    @SerialName(value = "DefaultPorts") val defaultPortsConfig: DefaultPortsConfig = DefaultPortsConfig.defaultConfig,
    @SerialName(value = "FeatureFlags") val featureFlags: FeatureFlags = FeatureFlags(),
    @SerialName(value = "SmartProtocol") val smartProtocolConfig: SmartProtocolConfig = SmartProtocolConfig.default,
    @SerialName(value = "RatingSettings") val ratingConfig: RatingConfig = RatingConfig.default,
    @SerialName(value = "LargeMetricsSamplingMultiplier")
    val largeMetricsSamplingMultiplier: Int = DEFAULT_LARGE_METRICS_SAMPLING_MULTIPLIER,
)

@Serializable
data class AppConfigResponseLegacyStorage(
    val underMaintenanceDetectionDelay: Long = Constants.DEFAULT_MAINTENANCE_CHECK_MINUTES,
    val logicalsRefreshForegroundDelayMinutesInternal: Long? = DEFAULT_SERVER_LIST_REFRESH_FOREGROUND_MINUTES,
    val logicalsRefreshBackgroundDelayMinutesInternal: Long? = DEFAULT_SERVER_LIST_REFRESH_BACKGROUND_MINUTES,
    val changeServerAttemptLimitInternal: Int? = DEFAULT_ATTEMPT_COUNT,
    val changeServerShortDelayInSecondsInternal: Int? = DEFAULT_CHANGE_SHORT_DELAY_SECONDS,
    val changeServerLongDelayInSecondsInternal: Int? = DEFAULT_CHANGE_LONG_DELAY_SECONDS,
    val defaultPortsConfig: DefaultPortsConfigLegacyStorage?,
    val featureFlags: FeatureFlagsLegacyStorage,
    val smartProtocolConfig: SmartProtocolConfigLegacyStorage?,
    val ratingConfig: RatingConfigLegacyStorage?,
    private val largeMetricsSamplingMultiplierInternal: Int? = DEFAULT_LARGE_METRICS_SAMPLING_MULTIPLIER,
) {
    // Workaround for GSON problem with deserializing fields with default values
    val changeServerAttemptLimit get() =
        changeServerAttemptLimitInternal ?: DEFAULT_ATTEMPT_COUNT
    val changeServerShortDelayInSeconds get() =
        changeServerShortDelayInSecondsInternal ?: DEFAULT_CHANGE_SHORT_DELAY_SECONDS
    val changeServerLongDelayInSeconds get() =
        changeServerLongDelayInSecondsInternal ?: DEFAULT_CHANGE_LONG_DELAY_SECONDS
    val logicalsRefreshForegroundDelayMinutes get() =
        logicalsRefreshForegroundDelayMinutesInternal ?: DEFAULT_SERVER_LIST_REFRESH_FOREGROUND_MINUTES
    val logicalsRefreshBackgroundDelayMinutes get() =
        logicalsRefreshBackgroundDelayMinutesInternal ?: DEFAULT_SERVER_LIST_REFRESH_BACKGROUND_MINUTES
    val largeMetricsSamplingMultiplier get() =
        largeMetricsSamplingMultiplierInternal ?: DEFAULT_LARGE_METRICS_SAMPLING_MULTIPLIER
}

fun AppConfigResponseLegacyStorage.migrate() = AppConfigResponse(
    underMaintenanceDetectionDelay = underMaintenanceDetectionDelay,
    logicalsRefreshBackgroundDelayMinutes = logicalsRefreshBackgroundDelayMinutes,
    logicalsRefreshForegroundDelayMinutes = logicalsRefreshForegroundDelayMinutes,
    changeServerAttemptLimit = changeServerAttemptLimit,
    changeServerShortDelayInSeconds = changeServerShortDelayInSeconds,
    changeServerLongDelayInSeconds = changeServerLongDelayInSeconds,
    defaultPortsConfig = defaultPortsConfig?.migrate() ?: DefaultPortsConfig.defaultConfig,
    featureFlags = featureFlags.migrate(),
    smartProtocolConfig = smartProtocolConfig?.migrate() ?: SmartProtocolConfig.default,
    ratingConfig = ratingConfig?.migrate() ?: RatingConfig.default,
    largeMetricsSamplingMultiplier = largeMetricsSamplingMultiplier
)
