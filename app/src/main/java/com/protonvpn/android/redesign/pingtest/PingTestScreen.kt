/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonOutlinedButton
import com.protonvpn.android.base.ui.ProtonSolidButton
import com.protonvpn.android.redesign.CountryId
import com.protonvpn.android.redesign.base.ui.Flag
import com.protonvpn.android.utils.CountryTools
import com.protonvpn.android.vpn.ui.LocalVpnUiDelegate
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.headlineSmall

@Composable
fun PingTestScreen(
    viewModel: PingTestViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val vpnUiDelegate = LocalVpnUiDelegate.current

    if (state.showStartConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmationDialog() },
            title = {
                Text(
                    text = "Start Server Connectivity Test",
                    style = ProtonTheme.typography.headlineSmall,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This test will attempt a real VPN connection to each server sequentially in alphabetical order.",
                        style = ProtonTheme.typography.defaultNorm
                    )
                    Text(
                        text = state.estimatedDurationText,
                        style = ProtonTheme.typography.captionNorm,
                        color = ProtonTheme.colors.brandNorm,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Each server will be tested up to ${state.attemptsPerServer} time(s).\n• Battery consumption may increase during testing.\n• Keeping the application open is recommended.",
                        style = ProtonTheme.typography.defaultSmallWeak
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.startTest(vpnUiDelegate) }) {
                    Text(
                        text = "Start Test",
                        color = ProtonTheme.colors.brandNorm,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmationDialog() }) {
                    Text(text = "Cancel", color = ProtonTheme.colors.textWeak)
                }
            },
            containerColor = ProtonTheme.colors.backgroundSecondary,
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProtonTheme.colors.backgroundNorm)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Server Connectivity Test",
                    style = ProtonTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sequential Real VPN Connection Testing",
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.textWeak,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(ProtonTheme.colors.brandNorm.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "REAL VPN",
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.brandNorm,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Configuration Field: Connection Attempts per Server
        if (!state.isTesting) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ProtonTheme.colors.backgroundSecondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connection Attempts per Server",
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Default: 1 attempt (Max: 10)",
                            style = ProtonTheme.typography.captionNorm,
                            color = ProtonTheme.colors.textWeak
                        )
                    }

                    OutlinedTextField(
                        value = state.attemptsText,
                        onValueChange = { viewModel.onAttemptsInputChanged(it) },
                        isError = state.attemptsError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = ProtonTheme.colors.textNorm
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .size(width = 64.dp, height = 48.dp)
                    )
                }
            }

            if (state.attemptsError != null) {
                Text(
                    text = state.attemptsError!!,
                    color = ProtonTheme.colors.notificationError,
                    style = ProtonTheme.typography.captionNorm,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Summary Statistics Card
        PingSummaryCard(state = state)

        Spacer(modifier = Modifier.height(12.dp))

        // Action Controls (Start Test / Stop Test / Progress)
        if (state.isTesting) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.currentTestingServer?.displayName ?: "Testing Servers...",
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.currentStepDescription ?: "Connecting...",
                            style = ProtonTheme.typography.captionNorm,
                            color = ProtonTheme.colors.brandNorm
                        )
                    }

                    Text(
                        text = "${state.testedCount} / ${state.totalCount} (${state.progressPercent}%)",
                        style = ProtonTheme.typography.captionNorm,
                        fontFamily = FontFamily.Monospace,
                        color = ProtonTheme.colors.textWeak
                    )
                }

                LinearProgressIndicator(
                    progress = { if (state.totalCount > 0) state.testedCount.toFloat() / state.totalCount.toFloat() else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ProtonTheme.colors.brandNorm,
                    trackColor = ProtonTheme.colors.backgroundSecondary
                )

                ProtonOutlinedButton(
                    onClick = { viewModel.cancelTest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Stop Test",
                        style = ProtonTheme.typography.defaultNorm,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            ProtonSolidButton(
                onClick = { viewModel.onRequestStartTest() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.attemptsError == null && state.totalCount > 0
            ) {
                Text(
                    text = "Start Test",
                    style = ProtonTheme.typography.defaultNorm,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dedicated Working List Section
        val workingList = state.workingServers
        if (workingList.isNotEmpty()) {
            Text(
                text = "WORKING SERVERS (${workingList.size})",
                style = ProtonTheme.typography.captionNorm,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ProtonTheme.colors.backgroundSecondary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (workingList.size > 2) 160.dp else 100.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(
                        items = workingList,
                        key = { "working_${it.server.serverId}" }
                    ) { item ->
                        WorkingServerRow(
                            item = item,
                            isConnected = state.activeConnectedServerId == item.server.serverId,
                            onConnectClick = { viewModel.connectToServer(vpnUiDelegate, item.server) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Server List Section Header
        val listTitle = if (state.isTesting) {
            "Testing Servers (Alphabetical Order)..."
        } else {
            "All Servers (${state.allTestedItems.size}) - Alphabetical Order"
        }

        Text(
            text = listTitle.uppercase(),
            style = ProtonTheme.typography.captionNorm,
            color = ProtonTheme.colors.textWeak,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // All Server List
        if (state.allTestedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ProtonTheme.colors.backgroundSecondary)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Press 'Start Test' to perform sequential real VPN connection tests on all available servers.",
                    style = ProtonTheme.typography.defaultSmallWeak,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = state.allTestedItems,
                    key = { it.server.serverId }
                ) { item ->
                    ServerTestRow(
                        item = item,
                        isConnected = state.activeConnectedServerId == item.server.serverId,
                        onConnectClick = { viewModel.connectToServer(vpnUiDelegate, item.server) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PingSummaryCard(state: PingTestUiState) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ProtonTheme.colors.backgroundSecondary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Test Summary",
                    style = ProtonTheme.typography.defaultNorm,
                    fontWeight = FontWeight.SemiBold
                )

                if (state.lastTestTime != null) {
                    Text(
                        text = "Last: ${state.lastTestTime}",
                        style = ProtonTheme.typography.captionNorm,
                        color = ProtonTheme.colors.textWeak,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatMetric(
                    label = "Total",
                    value = "${state.totalCount}",
                    color = ProtonTheme.colors.textNorm
                )
                StatMetric(
                    label = "Tested",
                    value = "${state.testedCount}",
                    color = ProtonTheme.colors.textNorm
                )
                StatMetric(
                    label = "Working",
                    value = "${state.workingCount}",
                    color = Color(0xFF4CAF50)
                )
                StatMetric(
                    label = "Failed",
                    value = "${state.failedCount}",
                    color = if (state.failedCount > 0) Color(0xFFE53935) else ProtonTheme.colors.textWeak
                )
            }
        }
    }
}

@Composable
private fun StatMetric(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = ProtonTheme.typography.defaultNorm,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 16.sp
        )
        Text(
            text = label,
            style = ProtonTheme.typography.captionNorm,
            color = ProtonTheme.colors.textWeak,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun WorkingServerRow(
    item: ServerPingItem,
    isConnected: Boolean,
    onConnectClick: () -> Unit
) {
    val server = item.server
    val countryName = CountryTools.getFullName(server.exitCountry)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ProtonTheme.colors.backgroundNorm)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Flag(
                exitCountry = CountryId(server.exitCountry),
                entryCountry = if (server.isSecureCoreServer) CountryId(server.entryCountry) else null,
                modifier = Modifier.size(22.dp)
            )

            Text(
                text = "${server.displayName} ($countryName)",
                style = ProtonTheme.typography.captionNorm,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (item.pingMs != null) {
                Text(
                    text = "${item.pingMs}ms",
                    style = ProtonTheme.typography.captionNorm,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isConnected) Color(0xFF4CAF50) else ProtonTheme.colors.brandNorm)
                    .clickable { onConnectClick() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isConnected) "Connected" else "Connect",
                    style = ProtonTheme.typography.captionNorm,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ServerTestRow(
    item: ServerPingItem,
    isConnected: Boolean,
    onConnectClick: () -> Unit
) {
    val server = item.server
    val countryName = CountryTools.getFullName(server.exitCountry)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ProtonTheme.colors.backgroundSecondary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Flag(
                    exitCountry = CountryId(server.exitCountry),
                    entryCountry = if (server.isSecureCoreServer) CountryId(server.entryCountry) else null,
                    modifier = Modifier.size(26.dp)
                )

                Column {
                    Text(
                        text = server.displayName,
                        style = ProtonTheme.typography.defaultNorm,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = countryName,
                        style = ProtonTheme.typography.captionNorm,
                        color = ProtonTheme.colors.textWeak,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (item.status) {
                    ServerTestStatus.NOT_TESTED -> {
                        Text(
                            text = "Not tested",
                            style = ProtonTheme.typography.captionNorm,
                            color = ProtonTheme.colors.textWeak
                        )
                    }

                    ServerTestStatus.TESTING_CONNECTING -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = ProtonTheme.colors.brandNorm
                            )
                            Text(
                                text = "Testing...",
                                style = ProtonTheme.typography.captionNorm,
                                color = ProtonTheme.colors.brandNorm,
                                fontSize = 11.sp
                            )
                        }
                    }

                    ServerTestStatus.AVAILABLE -> {
                        val ping = item.pingMs ?: 0L
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Working (${ping}ms)",
                                style = ProtonTheme.typography.captionNorm,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    ServerTestStatus.UNAVAILABLE, ServerTestStatus.TIMEOUT, ServerTestStatus.ERROR -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE53935).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Failed",
                                style = ProtonTheme.typography.captionNorm,
                                color = Color(0xFFE53935),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
