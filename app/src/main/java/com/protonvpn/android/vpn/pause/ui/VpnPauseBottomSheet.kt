/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.vpn.pause.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleModalBottomSheet
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.headlineSmall

private const val OPTION_1M = -2
private const val OPTION_5M = -1
private const val OPTION_15M = 0
private const val OPTION_30M = 1
private const val OPTION_45M = 2
private const val OPTION_1H = 3
private const val OPTION_CUSTOM = 4

private const val MAX_CUSTOM_MINUTES = 1440 // 24 hours maximum

@Composable
fun VpnPauseBottomSheet(
    onDismissRequest: () -> Unit,
    onPauseConfirmed: (durationMs: Long) -> Unit,
) {
    var selectedOption by remember { mutableIntStateOf(OPTION_1M) }
    var customMinutesText by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    SimpleModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("vpnPauseBottomSheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.vpn_pause_title),
                style = ProtonTheme.typography.headlineSmall,
                color = ProtonTheme.colors.textNorm
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn_pause_subtitle),
                style = ProtonTheme.typography.defaultSmallWeak,
                color = ProtonTheme.colors.textWeak
            )
            Spacer(Modifier.height(16.dp))

            val options = listOf(
                OPTION_1M to stringResource(R.string.vpn_pause_1m),
                OPTION_5M to stringResource(R.string.vpn_pause_5m),
                OPTION_15M to stringResource(R.string.vpn_pause_15m),
                OPTION_30M to stringResource(R.string.vpn_pause_30m),
                OPTION_45M to stringResource(R.string.vpn_pause_45m),
                OPTION_1H to stringResource(R.string.vpn_pause_1h),
                OPTION_CUSTOM to stringResource(R.string.vpn_pause_custom),
            )

            options.forEach { (optionId, label) ->
                val isSelected = selectedOption == optionId
                val itemShape = ProtonTheme.shapes.medium
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            if (isSelected) ProtonTheme.colors.backgroundSecondary else ProtonTheme.colors.backgroundNorm,
                            itemShape
                        )
                        .border(
                            1.dp,
                            if (isSelected) ProtonTheme.colors.textAccent else ProtonTheme.colors.separatorNorm,
                            itemShape
                        )
                        .clickable {
                            selectedOption = optionId
                            if (optionId != OPTION_CUSTOM) {
                                customError = null
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            selectedOption = optionId
                            if (optionId != OPTION_CUSTOM) {
                                customError = null
                            }
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ProtonTheme.colors.textAccent,
                            unselectedColor = ProtonTheme.colors.textWeak
                        )
                    )
                    Text(
                        text = label,
                        style = ProtonTheme.typography.defaultNorm,
                        color = ProtonTheme.colors.textNorm,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            if (selectedOption == OPTION_CUSTOM) {
                Spacer(Modifier.height(8.dp))
                val errorMsg = stringResource(R.string.vpn_pause_custom_error)
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        customMinutesText = digits
                        val parsed = digits.toIntOrNull()
                        customError = if (parsed == null || parsed <= 0 || parsed > MAX_CUSTOM_MINUTES) {
                            errorMsg
                        } else {
                            null
                        }
                    },
                    label = { Text(stringResource(R.string.vpn_pause_custom_hint)) },
                    isError = customError != null,
                    supportingText = customError?.let { { Text(it, color = ProtonTheme.colors.notificationError) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pauseCustomInput")
                )
            }

            Spacer(Modifier.height(24.dp))

            val isCustomValid = selectedOption != OPTION_CUSTOM || (customMinutesText.toIntOrNull()?.let { it in 1..MAX_CUSTOM_MINUTES } == true)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VpnWeakSolidButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                )

                VpnSolidButton(
                    text = stringResource(R.string.vpn_pause_confirm),
                    onClick = {
                        val durationMs: Long? = when (selectedOption) {
                            OPTION_1M -> 1 * 60 * 1000L
                            OPTION_5M -> 5 * 60 * 1000L
                            OPTION_15M -> 15 * 60 * 1000L
                            OPTION_30M -> 30 * 60 * 1000L
                            OPTION_45M -> 45 * 60 * 1000L
                            OPTION_1H -> 60 * 60 * 1000L
                            OPTION_CUSTOM -> {
                                val mins = customMinutesText.toIntOrNull()
                                if (mins != null && mins in 1..MAX_CUSTOM_MINUTES) {
                                    mins * 60 * 1000L
                                } else null
                            }
                            else -> null
                        }

                        if (durationMs != null && durationMs > 0) {
                            onPauseConfirmed(durationMs)
                            onDismissRequest()
                        }
                    },
                    enabled = isCustomValid,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("confirmPauseButton")
                )
            }
        }
    }
}
