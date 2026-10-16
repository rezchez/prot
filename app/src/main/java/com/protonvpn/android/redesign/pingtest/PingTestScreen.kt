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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import com.protonvpn.android.redesign.base.ui.ProtonAlert
import com.protonvpn.android.vpn.ui.LocalVpnUiDelegate
import me.proton.core.compose.theme.ProtonTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingTestScreen(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    viewModel: PingTestViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val vpnUiDelegate = LocalVpnUiDelegate.current

    // Confirmation Dialog before starting test (Requirement 2.6)
    if (state.showConfirmDialog) {
        val message = buildString {
            append("This will perform sequential real VPN connection tests on each server.\n\n")
            append("• Approximate estimated duration: ${state.estimatedDurationText}\n")
            append("• Testing servers sequentially (${state.totalCount} servers total)\n")
            append("• Attempts per server: ${state.attemptsPerServer}\n")
            if (state.hasActiveVpn) {
                append("• Warning: Your current VPN connection will be temporarily disconnected during testing.\n")
            }
            append("• Battery consumption may increase during extended testing.\n")
            append("• Keeping the application open is recommended for the most reliable testing.")
        }

        ProtonAlert(
            title = stringResource(R.string.ping_test_confirm_title),
            text = message,
            confirmLabel = "Start",
            onConfirm = { viewModel.onConfirmStart() },
            dismissLabel = "Cancel",
            onDismissRequest = { viewModel.onConfirmDialogDismiss() }
        )
    }

    // Country selection bottom sheet
    if (state.showCountryFilterSheet) {
        CountryFilterBottomSheet(
            state = state,
            onDismiss = { viewModel.onShowCountryFilter(false) },
            onToggleCountry = viewModel::onToggleCountry,
            onSelectAll = viewModel::onSelectAllCountries
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ProtonTheme.colors.backgroundNorm)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ping_test_title),
                style = ProtonTheme.typography.headline,
                fontWeight = FontWeight.Bold,
                color = ProtonTheme.colors.textNorm
            )

            if (onClose != null) {
                ProtonOutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(
                        text = "Close",
                        style = ProtonTheme.typography.body2Medium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Summary Card
        ServerTestSummaryCard(state)

        Spacer(modifier = Modifier.height(10.dp))

        // Configuration & Action Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ProtonTheme.colors.backgroundSecondary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!state.isTesting) {
                    // Country Selection Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ProtonTheme.colors.backgroundNorm)
                            .clickable { viewModel.onShowCountryFilter(true) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Test Countries",
                                style = ProtonTheme.typography.body1Medium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = state.selectedCountriesSummary,
                                style = ProtonTheme.typography.captionRegular,
                                color = ProtonTheme.colors.brandNorm
                            )
                        }

                        ProtonOutlinedButton(
                            onClick = { viewModel.onShowCountryFilter(true) },
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Select",
                                style = ProtonTheme.typography.body2Medium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Connection Attempts Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Connection Attempts per Server",
                                style = ProtonTheme.typography.body1Medium,
                                fontWeight = FontWeight.Medium
                            )
                            if (state.attemptsError != null) {
                                Text(
                                    text = state.attemptsError!!,
                                    style = ProtonTheme.typography.captionRegular,
                                    color = ProtonTheme.colors.notificationError,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.attemptsInputText,
                            onValueChange = viewModel::onAttemptsChanged,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = state.attemptsError != null,
                            modifier = Modifier
                                .width(70.dp)
                                .height(52.dp)
                        )
                    }

                    ProtonSolidButton(
                        onClick = { viewModel.onStartClicked() },
                        enabled = state.attemptsError == null && state.totalCount > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Start Test (${state.totalCount} Servers)",
                            style = ProtonTheme.typography.body1Medium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Ongoing test progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.currentTestingServer?.displayName ?: "Testing...",
                                style = ProtonTheme.typography.body1Medium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = state.currentStepDescription ?: "Connecting...",
                                style = ProtonTheme.typography.captionRegular,
                                color = if (state.isPaused) ProtonTheme.colors.notificationWarning else ProtonTheme.colors.brandNorm
                            )
                        }

                        Text(
                            text = "${state.testedCount} / ${state.totalCount} (${state.progressPercent}%)",
                            style = ProtonTheme.typography.captionRegular,
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
                        color = if (state.isPaused) ProtonTheme.colors.notificationWarning else ProtonTheme.colors.brandNorm,
                        trackColor = ProtonTheme.colors.backgroundNorm
                    )

                    // Control actions: Pause / Resume & Stop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProtonSolidButton(
                            onClick = {
                                if (state.isPaused) viewModel.onResumeClicked()
                                else viewModel.onPauseClicked()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (state.isPaused) "Resume Test" else "Pause Test",
                                style = ProtonTheme.typography.body1Medium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        ProtonOutlinedButton(
                            onClick = { viewModel.onCancelClicked() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Stop Test",
                                style = ProtonTheme.typography.body1Medium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs: Working Servers vs All Servers
        TabRow(
            selectedTabIndex = state.selectedTab.ordinal,
            containerColor = ProtonTheme.colors.backgroundNorm,
            contentColor = ProtonTheme.colors.brandNorm,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[state.selectedTab.ordinal]),
                    color = ProtonTheme.colors.brandNorm
                )
            }
        ) {
            Tab(
                selected = state.selectedTab == PingTestTab.WORKING,
                onClick = { viewModel.onTabSelected(PingTestTab.WORKING) },
                text = {
                    Text(
                        text = "Working (${state.workingServers.size})",
                        fontWeight = if (state.selectedTab == PingTestTab.WORKING) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = state.selectedTab == PingTestTab.ALL,
                onClick = { viewModel.onTabSelected(PingTestTab.ALL) },
                text = {
                    Text(
                        text = "All Servers (${state.allServers.size})",
                        fontWeight = if (state.selectedTab == PingTestTab.ALL) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Server List Content
        val currentList = if (state.selectedTab == PingTestTab.WORKING) {
            state.workingServers
        } else {
            state.allServers
        }

        if (currentList.isEmpty()) {
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
                    text = if (state.selectedTab == PingTestTab.WORKING) {
                        if (state.isTesting) "Testing servers sequentially..."
                        else if (state.lastTestTime != null) "No servers successfully connected during the test."
                        else "Press 'Start Test' to sequentially verify server connections."
                    } else {
                        "No servers available to display."
                    },
                    style = ProtonTheme.typography.body2Regular,
                    color = ProtonTheme.colors.textWeak,
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
                    items = currentList,
                    key = { it.server.serverId }
                ) { item ->
                    ServerTestItemRow(
                        item = item,
                        isConnected = state.activeConnectedServerId == item.server.serverId,
                        isProfileSaved = state.savedProfileServerIds.contains(item.server.serverId),
                        onConnectClick = { viewModel.onConnectToServer(vpnUiDelegate, item.server) },
                        onAddToProfileClick = { viewModel.onAddServerToProfile(item.server) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryFilterBottomSheet(
    state: PingTestUiState,
    onDismiss: () -> Unit,
    onToggleCountry: (String) -> Unit,
    onSelectAll: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(state.availableCountries, searchQuery) {
        if (searchQuery.isBlank()) state.availableCountries
        else state.availableCountries.filter {
            it.countryName.contains(searchQuery, ignoreCase = true) ||
                it.countryCode.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ProtonTheme.colors.backgroundNorm,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Countries for Ping Test",
                    style = ProtonTheme.typography.body1Medium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onSelectAll) {
                    Text(
                        text = if (state.selectedCountryCodes.isEmpty()) "Selected: All" else "Select All",
                        style = ProtonTheme.typography.body2Medium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search country...", style = ProtonTheme.typography.captionRegular) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredCountries, key = { it.countryCode }) { country ->
                    val isSelected = state.selectedCountryCodes.isEmpty() || state.selectedCountryCodes.contains(country.countryCode)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleCountry(country.countryCode) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Flag(
                                exitCountry = CountryId(country.countryCode),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                            Text(
                                text = country.countryName,
                                style = ProtonTheme.typography.body1Regular,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "(${country.serverCount})",
                                style = ProtonTheme.typography.captionRegular,
                                color = ProtonTheme.colors.textWeak
                            )
                        }

                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleCountry(country.countryCode) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ProtonSolidButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Apply (${state.totalCount} Servers)")
            }
        }
    }
}

@Composable
private fun ServerTestSummaryCard(state: PingTestUiState) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ProtonTheme.colors.backgroundSecondary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Test Statistics",
                    style = ProtonTheme.typography.body1Medium,
                    fontWeight = FontWeight.SemiBold
                )

                if (state.lastTestTime != null) {
                    Text(
                        text = "Last: ${state.lastTestTime}",
                        style = ProtonTheme.typography.captionRegular,
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
                    label = "Total Servers",
                    value = "${state.totalCount}",
                    color = ProtonTheme.colors.textNorm
                )
                StatMetric(
                    label = "Working",
                    value = "${state.workingCount}",
                    color = ProtonTheme.colors.notificationSuccess
                )
                StatMetric(
                    label = "Failed",
                    value = "${state.failedCount}",
                    color = ProtonTheme.colors.notificationError
                )
                StatMetric(
                    label = "Tested",
                    value = "${state.testedCount}",
                    color = ProtonTheme.colors.brandNorm
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
            style = ProtonTheme.typography.body1Medium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = ProtonTheme.typography.captionRegular,
            color = ProtonTheme.colors.textWeak,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ServerTestItemRow(
    item: ServerTestItem,
    isConnected: Boolean,
    isProfileSaved: Boolean,
    onConnectClick: () -> Unit,
    onAddToProfileClick: () -> Unit
) {
    val serverNumber = remember(item.server.serverName) {
        val num = item.server.serverName.substringAfterLast('#', "")
        if (num.isNotEmpty()) "#$num" else ""
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = ProtonTheme.colors.backgroundSecondary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Flag(
                    exitCountry = CountryId(item.server.exitCountry),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.server.displayName,
                            style = ProtonTheme.typography.body1Medium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Distinctive server number badge
                        if (serverNumber.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (item.status == ServerTestStatus.WORKING)
                                            ProtonTheme.colors.brandNorm.copy(alpha = 0.2f)
                                        else
                                            ProtonTheme.colors.backgroundNorm
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = serverNumber,
                                    style = ProtonTheme.typography.captionRegular,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.status == ServerTestStatus.WORKING)
                                        ProtonTheme.colors.brandNorm
                                    else
                                        ProtonTheme.colors.textWeak,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    val statusText = when (item.status) {
                        ServerTestStatus.NOT_TESTED -> "Not tested"
                        ServerTestStatus.TESTING -> "Testing (Attempt ${item.currentAttempt}/${item.maxAttempts})..."
                        ServerTestStatus.WORKING -> {
                            val timeStr = item.connectTimeMs?.let { "${it / 1000f}s" } ?: ""
                            "Working • Connected in $timeStr"
                        }
                        ServerTestStatus.FAILED -> "Failed after ${item.maxAttempts} attempt(s)"
                    }
                    val statusColor = when (item.status) {
                        ServerTestStatus.NOT_TESTED -> ProtonTheme.colors.textWeak
                        ServerTestStatus.TESTING -> ProtonTheme.colors.brandNorm
                        ServerTestStatus.WORKING -> ProtonTheme.colors.notificationSuccess
                        ServerTestStatus.FAILED -> ProtonTheme.colors.notificationError
                    }
                    Text(
                        text = statusText,
                        style = ProtonTheme.typography.captionRegular,
                        color = statusColor
                    )
                }
            }

            // Right side: Status badge or actions (Connect + Add to Profile)
            if (item.status == ServerTestStatus.TESTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = ProtonTheme.colors.brandNorm
                )
            } else if (item.status == ServerTestStatus.WORKING) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Add to profile action
                    if (isProfileSaved) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ProtonTheme.colors.notificationSuccess.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "✓ Profile",
                                style = ProtonTheme.typography.captionRegular,
                                fontWeight = FontWeight.Bold,
                                color = ProtonTheme.colors.notificationSuccess,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        ProtonOutlinedButton(
                            onClick = onAddToProfileClick,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = "+ Profile",
                                style = ProtonTheme.typography.captionRegular,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Connect action
                    if (isConnected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ProtonTheme.colors.notificationSuccess.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "CONNECTED",
                                style = ProtonTheme.typography.captionRegular,
                                fontWeight = FontWeight.Bold,
                                color = ProtonTheme.colors.notificationSuccess,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        ProtonSolidButton(
                            onClick = onConnectClick,
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                text = "Connect",
                                style = ProtonTheme.typography.captionRegular,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
