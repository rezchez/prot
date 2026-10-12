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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProtonTheme.colors.backgroundNorm)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Header with Title & Feature Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Secure Core Ping Test",
                    style = ProtonTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stealth Protocol • Cloudflare (1.1.1.1) Trace",
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
                    text = "STEALTH",
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.brandNorm,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Summary Statistics Card
        PingSummaryCard(state = state)

        Spacer(modifier = Modifier.height(12.dp))

        // Test Action Controls (Start / Cancel / Live Step Progress)
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
                            text = state.currentTestingServer?.displayName ?: "Testing Secure Core...",
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.currentStepDescription ?: "Connecting via Stealth...",
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
                onClick = { viewModel.startTest(vpnUiDelegate) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Test Connection (Stealth)",
                    style = ProtonTheme.typography.defaultNorm,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Server List Section Header
        val listTitle = if (state.isTesting) {
            "Testing Secure Core Servers..."
        } else if (state.availableSortedItems.isNotEmpty()) {
            "Available Servers (${state.availableSortedItems.size}) - Lowest Ping First"
        } else {
            "Secure Core Servers"
        }

        Text(
            text = listTitle.uppercase(),
            style = ProtonTheme.typography.captionNorm,
            color = ProtonTheme.colors.textWeak,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Server List
        val displayItems = if (state.isTesting) {
            state.allTestedItems
        } else {
            state.availableSortedItems
        }

        if (displayItems.isEmpty()) {
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
                    text = if (state.lastTestTime != null) {
                        "No reachable Secure Core servers found via Stealth on the current network."
                    } else {
                        "Click 'Test Connection' to test all Secure Core servers via Stealth and measure latency to Cloudflare."
                    },
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
                    items = displayItems,
                    key = { it.server.serverId }
                ) { item ->
                    SecureCoreServerItemRow(
                        item = item,
                        isConnected = state.activeConnectedServerId == item.server.serverId,
                        onConnectClick = { viewModel.connectToSecureCore(vpnUiDelegate, item.server) }
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
        colors = CardDefaults.cardColors(
            containerColor = ProtonTheme.colors.backgroundSecondary
        ),
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
                    text = "Test Statistics",
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
                    label = "Secure Core",
                    value = "${state.totalCount}",
                    color = ProtonTheme.colors.textNorm
                )
                StatMetric(
                    label = "Tested",
                    value = "${state.testedCount}",
                    color = ProtonTheme.colors.textNorm
                )
                StatMetric(
                    label = "Available",
                    value = "${state.availableCount}",
                    color = Color(0xFF4CAF50)
                )
                StatMetric(
                    label = "Unavailable",
                    value = "${state.unavailableCount}",
                    color = if (state.unavailableCount > 0) Color(0xFFE53935) else ProtonTheme.colors.textWeak
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
private fun SecureCoreServerItemRow(
    item: ServerPingItem,
    isConnected: Boolean,
    onConnectClick: () -> Unit
) {
    val server = item.server
    val entryFullName = CountryTools.getFullName(server.entryCountry)
    val exitFullName = CountryTools.getFullName(server.exitCountry)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) ProtonTheme.colors.backgroundSecondary.copy(alpha = 0.8f)
            else ProtonTheme.colors.backgroundSecondary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.status == ServerTestStatus.AVAILABLE) {
                onConnectClick()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flags & Route Details
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Double Flag (Entry -> Exit)
                Flag(
                    exitCountry = CountryId(server.exitCountry),
                    entryCountry = CountryId(server.entryCountry),
                    modifier = Modifier.size(28.dp)
                )

                Column(modifier = Modifier.padding(start = 2.dp)) {
                    Text(
                        text = "$entryFullName >> $exitFullName",
                        style = ProtonTheme.typography.defaultNorm,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = server.serverName,
                            style = ProtonTheme.typography.captionNorm,
                            fontFamily = FontFamily.Monospace,
                            color = ProtonTheme.colors.textWeak,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ProtonTheme.colors.backgroundNorm)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Stealth",
                                style = ProtonTheme.typography.captionNorm,
                                color = ProtonTheme.colors.brandNorm,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Status Badge & Connect Action
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
                                text = "Connecting...",
                                style = ProtonTheme.typography.captionNorm,
                                color = ProtonTheme.colors.brandNorm,
                                fontSize = 11.sp
                            )
                        }
                    }

                    ServerTestStatus.TESTING_CLOUDFLARE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFFA000)
                            )
                            Text(
                                text = "Pinging 1.1.1.1...",
                                style = ProtonTheme.typography.captionNorm,
                                color = Color(0xFFFFA000),
                                fontSize = 11.sp
                            )
                        }
                    }

                    ServerTestStatus.AVAILABLE -> {
                        val ping = item.pingMs ?: 0L
                        val pingColor = when {
                            ping < 100 -> Color(0xFF4CAF50)
                            ping < 200 -> Color(0xFFFFA000)
                            else -> Color(0xFFE53935)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(pingColor.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$ping ms",
                                style = ProtonTheme.typography.captionNorm,
                                fontFamily = FontFamily.Monospace,
                                color = pingColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isConnected) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF4CAF50))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Connected",
                                    style = ProtonTheme.typography.captionNorm,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ProtonTheme.colors.brandNorm)
                                    .clickable { onConnectClick() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Connect",
                                    style = ProtonTheme.typography.captionNorm,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    ServerTestStatus.TIMEOUT -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE53935).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Timeout",
                                style = ProtonTheme.typography.captionNorm,
                                color = Color(0xFFE53935),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    ServerTestStatus.UNAVAILABLE -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ProtonTheme.colors.backgroundNorm)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Blocked",
                                style = ProtonTheme.typography.captionNorm,
                                color = ProtonTheme.colors.textWeak,
                                fontSize = 11.sp
                            )
                        }
                    }

                    ServerTestStatus.ERROR -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE53935).copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Error",
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
