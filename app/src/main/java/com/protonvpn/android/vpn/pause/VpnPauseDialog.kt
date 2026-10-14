/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.vpn.pause

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.protonvpn.android.R
import me.proton.core.compose.theme.ProtonTheme

@Composable
fun VpnPauseDialog(
    onDismiss: () -> Unit,
    onSelectDuration: (durationMs: Long) -> Unit
) {
    var showCustomInput by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = ProtonTheme.colors.backgroundSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ProtonTheme.colors.brandNorm.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pause),
                        contentDescription = null,
                        tint = ProtonTheme.colors.brandNorm,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(id = R.string.pause_vpn_duration_title),
                    style = ProtonTheme.typography.body1Medium,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtonTheme.colors.textNorm,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!showCustomInput) {
                    val options = listOf(
                        15 to stringResource(R.string.pause_duration_15m),
                        30 to stringResource(R.string.pause_duration_30m),
                        45 to stringResource(R.string.pause_duration_45m),
                        60 to stringResource(R.string.pause_duration_1h)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (mins, label) ->
                            PauseOptionRow(label = label) {
                                onSelectDuration(mins * 60 * 1000L)
                                onDismiss()
                            }
                        }

                        PauseOptionRow(label = stringResource(R.string.pause_duration_custom)) {
                            showCustomInput = true
                        }
                    }
                } else {
                    Text(
                        text = stringResource(id = R.string.pause_custom_title),
                        style = ProtonTheme.typography.body2Regular,
                        color = ProtonTheme.colors.textWeak,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(4)
                            customMinutesText = digits
                            val parsed = digits.toLongOrNull()
                            errorMessage = if (parsed == null || parsed <= 0 || parsed > 1440) {
                                "Enter 1 to 1440 minutes (max 24 hours)"
                            } else null
                        },
                        isError = errorMessage != null,
                        singleLine = true,
                        placeholder = {
                            Text(
                                stringResource(R.string.pause_custom_hint),
                                style = TextStyle(color = ProtonTheme.colors.textWeak, fontSize = 14.sp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = ProtonTheme.colors.textNorm
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage!!,
                            color = ProtonTheme.colors.notificationError,
                            style = ProtonTheme.typography.body2Regular,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ProtonTheme.colors.backgroundNorm)
                                .clickable { showCustomInput = false }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Back",
                                style = ProtonTheme.typography.body1Medium,
                                fontWeight = FontWeight.Medium,
                                color = ProtonTheme.colors.textWeak
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ProtonTheme.colors.brandNorm)
                                .clickable {
                                    val mins = customMinutesText.toLongOrNull()
                                    if (mins != null && mins in 1..1440) {
                                        onSelectDuration(mins * 60 * 1000L)
                                        onDismiss()
                                    } else {
                                        errorMessage = "Enter 1 to 1440 minutes (max 24 hours)"
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Pause",
                                style = ProtonTheme.typography.body1Medium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PauseOptionRow(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ProtonTheme.colors.backgroundNorm)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            style = ProtonTheme.typography.body1Medium,
            fontWeight = FontWeight.Medium,
            color = ProtonTheme.colors.textNorm
        )
    }
}
