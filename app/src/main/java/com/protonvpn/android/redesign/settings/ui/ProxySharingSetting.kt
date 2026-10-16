/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.protonvpn.android.proxysharing.ConnectedDevice
import com.protonvpn.android.proxysharing.ConnectedDeviceManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.ProtonSwitch
import com.protonvpn.android.base.ui.SettingsFeatureToggle
import com.protonvpn.android.proxysharing.ProxySharingManager
import com.protonvpn.android.proxysharing.ProxySharingState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.headlineSmall
import java.util.Locale

@Composable
fun ProxySharingSetting(
    onClose: (() -> Unit)? = null,
    onEnableProxySharing: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by ProxySharingManager.state.collectAsStateWithLifecycle()
    val connectedDevices by ConnectedDeviceManager.devicesFlow.collectAsStateWithLifecycle()

    var editingSocksPort by remember { mutableStateOf(false) }
    var editingHttpPort by remember { mutableStateOf(false) }
    var renamingDevice by remember { mutableStateOf<ConnectedDevice?>(null) }
    var limitingDevice by remember { mutableStateOf<ConnectedDevice?>(null) }

    if (renamingDevice != null) {
        RenameDeviceDialog(
            device = renamingDevice!!,
            onDismiss = { renamingDevice = null },
            onSave = { newName ->
                ConnectedDeviceManager.setDeviceName(renamingDevice!!.ip, newName)
                renamingDevice = null
            }
        )
    }

    if (limitingDevice != null) {
        SetDailyLimitDialog(
            device = limitingDevice!!,
            onDismiss = { limitingDevice = null },
            onSave = { limitBytes ->
                ConnectedDeviceManager.setDeviceDailyLimit(limitingDevice!!.ip, limitBytes)
                limitingDevice = null
            }
        )
    }

    if (editingSocksPort) {
        PortConfigDialog(
            title = "Set SOCKS5 Port",
            initialPort = state.socksPort,
            onDismiss = { editingSocksPort = false },
            onSave = { newPort ->
                ProxySharingManager.setSocksPort(context, newPort)
                Toast.makeText(context, "SOCKS5 port set to $newPort", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (editingHttpPort) {
        PortConfigDialog(
            title = "Set HTTP/HTTPS Port",
            initialPort = state.httpPort,
            onDismiss = { editingHttpPort = false },
            onSave = { newPort ->
                ProxySharingManager.setHttpPort(context, newPort)
                Toast.makeText(context, "HTTP port set to $newPort", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val sharingContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            // Master Feature Toggle
            SettingsFeatureToggle(
                label = stringResource(id = R.string.settings_proxy_sharing_master_toggle),
                checked = state.isSharingEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !state.isSharingEnabled) {
                        onEnableProxySharing()
                    }
                    ProxySharingManager.toggleMaster(context, enabled)
                }
            )

            Text(
                text = stringResource(id = R.string.settings_proxy_sharing_master_description),
                style = ProtonTheme.typography.defaultSmallWeak,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Info Card displaying Wi-Fi IP and Proxy Ports
            ProxyInfoCard(
                state = state,
                context = context,
                onEditSocksPort = { editingSocksPort = true },
                onEditHttpPort = { editingHttpPort = true }
            )

            // Statistics & Real-Time Download / Upload Speed Graph
            ProxyStatisticsCard(state = state, context = context)

            // Connected Devices List (Right below the graph)
            ConnectedDevicesSection(
                devices = connectedDevices,
                onRename = { dev -> renamingDevice = dev },
                onSetDailyLimit = { dev -> limitingDevice = dev },
                onToggleBlock = { dev ->
                    ConnectedDeviceManager.setDeviceBlocked(dev.ip, !dev.isBlocked)
                },
                onRemove = { dev ->
                    ConnectedDeviceManager.removeDevice(dev.ip)
                }
            )

            // Server Selection Toggles
            Text(
                text = "PROXY PROTOCOLS",
                style = ProtonTheme.typography.captionNorm,
                color = ProtonTheme.colors.textWeak,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ProtonTheme.colors.backgroundSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ProtocolToggleRow(
                        title = stringResource(id = R.string.settings_proxy_sharing_socks_title),
                        subtitle = "0.0.0.0:${state.socksPort} (SOCKS5)",
                        checked = state.isSocksEnabled,
                        onCheckedChange = { ProxySharingManager.toggleSocks(context, it) },
                        onEditPort = { editingSocksPort = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ProtocolToggleRow(
                        title = stringResource(id = R.string.settings_proxy_sharing_http_title),
                        subtitle = "0.0.0.0:${state.httpPort} (HTTP / HTTPS)",
                        checked = state.isHttpEnabled,
                        onCheckedChange = { ProxySharingManager.toggleHttp(context, it) },
                        onEditPort = { editingHttpPort = true }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ProtocolToggleRow(
                        title = stringResource(id = R.string.settings_proxy_sharing_pac_title),
                        subtitle = "0.0.0.0:${state.pacPort} (Auto-Config)",
                        checked = state.isPacEnabled,
                        onCheckedChange = { ProxySharingManager.togglePac(context, it) }
                    )
                }
            }

            // Client Configuration Guide
            Text(
                text = "CLIENT SETUP INSTRUCTIONS",
                style = ProtonTheme.typography.captionNorm,
                color = ProtonTheme.colors.textWeak,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )

            SetupInstructionsCard(state = state)

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (onClose != null) {
        SubSetting(
            title = stringResource(id = R.string.settings_proxy_sharing_title),
            onClose = onClose
        ) {
            sharingContent()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.bottom_nav_vpn_sharing),
                    style = ProtonTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            sharingContent()
        }
    }
}

@Composable
private fun ProxyInfoCard(
    state: ProxySharingState,
    context: Context,
    onEditSocksPort: () -> Unit = {},
    onEditHttpPort: () -> Unit = {}
) {
    val activeColor = Color(0xFF4CAF50)
    val inactiveColor = ProtonTheme.colors.textHint

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = ProtonTheme.colors.backgroundSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.settings_proxy_sharing_info_card_title),
                    style = ProtonTheme.typography.headlineSmall,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (state.isSharingEnabled) activeColor else inactiveColor)
                    )
                    Text(
                        text = if (state.isSharingEnabled) {
                            stringResource(id = R.string.settings_proxy_sharing_status_active)
                        } else {
                            stringResource(id = R.string.settings_proxy_sharing_status_inactive)
                        },
                        style = ProtonTheme.typography.captionNorm,
                        color = if (state.isSharingEnabled) activeColor else inactiveColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Outbound Tunnel Indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProtonTheme.colors.backgroundNorm)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.settings_proxy_sharing_vpn_status_label),
                    style = ProtonTheme.typography.defaultSmallWeak
                )
                Text(
                    text = if (state.isVpnConnected) "Routing via Tunnel (Active)" else "Direct (VPN not connected)",
                    style = ProtonTheme.typography.captionNorm,
                    color = if (state.isVpnConnected) activeColor else ProtonTheme.colors.textWeak,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Info items with Copy button
            InfoRowWithCopy(
                label = stringResource(id = R.string.settings_proxy_sharing_local_ip_label),
                value = state.localIp,
                context = context
            )

            InfoRowWithCopy(
                label = "SOCKS5 Port",
                value = "${state.socksPort}",
                fullCopyText = "${state.localIp}:${state.socksPort}",
                context = context,
                onEdit = onEditSocksPort
            )

            InfoRowWithCopy(
                label = "HTTP/HTTPS Port",
                value = "${state.httpPort}",
                fullCopyText = "${state.localIp}:${state.httpPort}",
                context = context,
                onEdit = onEditHttpPort
            )

            val pacUrl = "http://${state.localIp}:${state.pacPort}/proxy.pac"
            InfoRowWithCopy(
                label = stringResource(id = R.string.settings_proxy_sharing_pac_url_label),
                value = pacUrl,
                context = context
            )
        }
    }
}

@Composable
private fun InfoRowWithCopy(
    label: String,
    value: String,
    fullCopyText: String = value,
    context: Context,
    onEdit: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ProtonTheme.colors.backgroundNorm)
            .clickable {
                copyToClipboard(context, fullCopyText)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = ProtonTheme.typography.captionNorm,
                color = ProtonTheme.colors.textWeak
            )
            Text(
                text = value,
                style = ProtonTheme.typography.defaultNorm,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onEdit != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ProtonTheme.colors.backgroundSecondary)
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit_port),
                        contentDescription = "Edit Port",
                        tint = ProtonTheme.colors.brandNorm,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(ProtonTheme.colors.backgroundSecondary)
                    .clickable { copyToClipboard(context, fullCopyText) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_copy),
                    contentDescription = "Copy",
                    tint = ProtonTheme.colors.brandNorm,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProtocolToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditPort: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onEditPort != null) Modifier.clickable { onEditPort() } else Modifier
                )
        ) {
            Text(
                text = title,
                style = ProtonTheme.typography.defaultNorm,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = subtitle,
                    style = ProtonTheme.typography.defaultSmallWeak,
                    fontFamily = FontFamily.Monospace
                )
                if (onEditPort != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(ProtonTheme.colors.backgroundNorm)
                            .clickable { onEditPort() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit_port),
                            contentDescription = "Change Port",
                            tint = ProtonTheme.colors.brandNorm,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        ProtonSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SetupInstructionsCard(state: ProxySharingState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = ProtonTheme.colors.backgroundSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Connecting Other Devices",
                style = ProtonTheme.typography.headlineSmall,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            InstructionItem(
                device = "Windows & Mac",
                instruction = "In Network/Proxy Settings, choose 'Automatic proxy setup' and paste the PAC URL, or enter Manual Proxy: ${state.localIp}:${state.httpPort}."
            )

            InstructionItem(
                device = "iOS & iPadOS",
                instruction = "Go to Wi-Fi settings > (i) > Configure Proxy > Automatic > enter PAC URL: http://${state.localIp}:${state.pacPort}/proxy.pac."
            )

            InstructionItem(
                device = "Android & Android TV",
                instruction = "Go to Wi-Fi network details > Advanced > Proxy > Manual > enter Host ${state.localIp} and Port ${state.httpPort}."
            )

            InstructionItem(
                device = "Browser Extensions / Telegram",
                instruction = "Configure SOCKS5 proxy to ${state.localIp}:${state.socksPort} (No authentication required)."
            )
        }
    }
}

@Composable
private fun InstructionItem(device: String, instruction: String) {
    Column {
        Text(
            text = device,
            style = ProtonTheme.typography.defaultNorm,
            fontWeight = FontWeight.Bold,
            color = ProtonTheme.colors.brandNorm
        )
        Text(
            text = instruction,
            style = ProtonTheme.typography.defaultSmallWeak,
            lineHeight = 18.sp
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Proxy Config", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.settings_proxy_sharing_copied), Toast.LENGTH_SHORT).show()
}

private fun formatToMB(bytes: Long): String {
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        val gb = mb / 1024.0
        String.format(Locale.US, "%.2f GB", gb)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

@Composable
private fun ProxyStatisticsCard(
    state: ProxySharingState,
    context: Context
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = ProtonTheme.colors.backgroundSecondary
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Reset button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRAFFIC & REAL-TIME SPEED",
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.textWeak,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Reset",
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.brandNorm,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { ProxySharingManager.resetStatistics(context) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Real-time speed gauges (Download & Upload)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Download Speed
                SpeedBadge(
                    label = "Download",
                    speedMbps = state.currentDownloadSpeedMbps,
                    indicatorColor = Color(0xFF22C55E),
                    modifier = Modifier.weight(1f)
                )

                // Upload Speed
                SpeedBadge(
                    label = "Upload",
                    speedMbps = state.currentUploadSpeedMbps,
                    indicatorColor = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f)
                )
            }

            // Speed Graph (Time Series Canvas)
            SpeedTimeSeriesGraph(
                history = state.speedHistory,
                isSharing = state.isSharingEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )

            // Volume breakdown (MB)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ProtonTheme.colors.backgroundNorm)
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                VolumeItem(
                    label = "Downloaded",
                    value = formatToMB(state.totalBytesDownloaded)
                )
                VolumeItem(
                    label = "Uploaded",
                    value = formatToMB(state.totalBytesUploaded)
                )
                VolumeItem(
                    label = "Total Volume",
                    value = formatToMB(state.totalBytesTransferred)
                )
            }
        }
    }
}

@Composable
private fun SpeedBadge(
    label: String,
    speedMbps: Float,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ProtonTheme.colors.backgroundNorm)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Text(
                text = label,
                style = ProtonTheme.typography.captionNorm,
                color = ProtonTheme.colors.textWeak,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = String.format(Locale.US, "%.1f Mbps", speedMbps),
            style = ProtonTheme.typography.captionNorm,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SpeedTimeSeriesGraph(
    history: List<com.protonvpn.android.proxysharing.SpeedDataPoint>,
    isSharing: Boolean,
    modifier: Modifier = Modifier
) {
    val downloadColor = Color(0xFF22C55E)
    val uploadColor = Color(0xFF8B5CF6)
    val gridColor = ProtonTheme.colors.textHint.copy(alpha = 0.2f)
    val textColor = ProtonTheme.colors.textWeak

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ProtonTheme.colors.backgroundNorm)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!isSharing) {
            Text(
                text = "Proxy sharing is inactive",
                style = ProtonTheme.typography.captionNorm,
                color = textColor
            )
        } else if (history.size < 2) {
            Text(
                text = "Waiting for network traffic...",
                style = ProtonTheme.typography.captionNorm,
                color = textColor
            )
        } else {
            Canvas(modifier = Modifier.fillMaxWidth().height(94.dp)) {
                val width = size.width
                val height = size.height

                val maxSpeed = maxOf(
                    1f,
                    history.maxOfOrNull { maxOf(it.downloadSpeedMbps, it.uploadSpeedMbps) } ?: 1f
                )

                // Background grid lines (horizontal)
                val steps = 3
                for (i in 0..steps) {
                    val y = height * (i.toFloat() / steps)
                    drawLine(
                        color = gridColor,
                        start = androidx.compose.ui.geometry.Offset(0f, y),
                        end = androidx.compose.ui.geometry.Offset(width, y),
                        strokeWidth = 1f
                    )
                }

                fun buildPath(getter: (com.protonvpn.android.proxysharing.SpeedDataPoint) -> Float): Path {
                    val path = Path()
                    val stepX = width / (history.size - 1).coerceAtLeast(1)
                    history.forEachIndexed { index, point ->
                        val x = index * stepX
                        val speed = getter(point)
                        val y = height - (speed / maxSpeed) * (height - 6f) - 3f
                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    return path
                }

                val downloadPath = buildPath { it.downloadSpeedMbps }
                val uploadPath = buildPath { it.uploadSpeedMbps }

                drawPath(
                    path = downloadPath,
                    color = downloadColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                drawPath(
                    path = uploadPath,
                    color = uploadColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun VolumeItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = ProtonTheme.typography.captionNorm,
            color = ProtonTheme.colors.textWeak,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = ProtonTheme.typography.captionNorm,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PortConfigDialog(
    title: String,
    initialPort: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(initialPort.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val isSocks = title.contains("SOCKS", ignoreCase = true)
    val presets = if (isSocks) listOf(10808, 1080, 9050, 8889) else listOf(10809, 8080, 8888, 3128)

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
                // Header Icon Badge
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ProtonTheme.colors.brandNorm.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit_port),
                        contentDescription = null,
                        tint = ProtonTheme.colors.brandNorm,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    style = ProtonTheme.typography.headlineSmall,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtonTheme.colors.textNorm,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Specify a local port between 1024 and 65535 for proxy connections.",
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.textWeak,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Presets
                Text(
                    text = "Quick Presets:",
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.textWeak,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = textValue == preset.toString()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) ProtonTheme.colors.brandNorm.copy(alpha = 0.2f)
                                    else ProtonTheme.colors.backgroundNorm
                                )
                                .clickable {
                                    textValue = preset.toString()
                                    errorText = null
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.toString(),
                                style = ProtonTheme.typography.captionNorm,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) ProtonTheme.colors.brandNorm else ProtonTheme.colors.textNorm
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input Field
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(5)
                        textValue = digits
                        val parsed = digits.toIntOrNull()
                        errorText = if (parsed == null || parsed !in 1024..65535) {
                            "Port must be between 1024 and 65535"
                        } else null
                    },
                    isError = errorText != null,
                    singleLine = true,
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

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorText!!,
                        color = ProtonTheme.colors.notificationError,
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ProtonTheme.colors.backgroundNorm)
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = ProtonTheme.typography.defaultNorm,
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
                                val port = textValue.toIntOrNull()
                                if (port != null && port in 1024..65535) {
                                    onSave(port)
                                    onDismiss()
                                } else {
                                    errorText = "Port must be between 1024 and 65535"
                                }
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save Port",
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
    val seconds = totalSeconds % 60
    val totalMinutes = totalSeconds / 60
    val minutes = totalMinutes % 60
    val hours = totalMinutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

@Composable
private fun ConnectedDevicesSection(
    devices: List<ConnectedDevice>,
    onRename: (ConnectedDevice) -> Unit,
    onSetDailyLimit: (ConnectedDevice) -> Unit,
    onToggleBlock: (ConnectedDevice) -> Unit,
    onRemove: (ConnectedDevice) -> Unit
) {
    val activeCount = devices.count { it.isCurrentlyConnected }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.settings_proxy_sharing_connected_devices_title).uppercase(Locale.ROOT),
                style = ProtonTheme.typography.captionNorm,
                color = ProtonTheme.colors.textWeak,
                fontWeight = FontWeight.Bold
            )

            if (devices.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (activeCount > 0) Color(0xFF22C55E) else ProtonTheme.colors.textHint)
                    )
                    Text(
                        text = "$activeCount active",
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 11.sp,
                        color = if (activeCount > 0) Color(0xFF22C55E) else ProtonTheme.colors.textWeak,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ProtonTheme.colors.backgroundSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ProtonTheme.colors.backgroundNorm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_device_client),
                            contentDescription = null,
                            tint = ProtonTheme.colors.textWeak,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(id = R.string.settings_proxy_sharing_connected_devices_empty),
                        style = ProtonTheme.typography.defaultNorm,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.settings_proxy_sharing_connected_devices_empty_desc),
                        style = ProtonTheme.typography.captionNorm,
                        color = ProtonTheme.colors.textWeak,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            devices.forEach { device ->
                ConnectedDeviceCard(
                    device = device,
                    onRename = { onRename(device) },
                    onSetDailyLimit = { onSetDailyLimit(device) },
                    onToggleBlock = { onToggleBlock(device) },
                    onRemove = { onRemove(device) }
                )
            }
        }
    }
}

@Composable
private fun ConnectedDeviceCard(
    device: ConnectedDevice,
    onRename: () -> Unit,
    onSetDailyLimit: () -> Unit,
    onToggleBlock: () -> Unit,
    onRemove: () -> Unit
) {
    val activeColor = Color(0xFF22C55E)
    val blockedColor = ProtonTheme.colors.notificationError
    val inactiveColor = ProtonTheme.colors.textWeak

    val statusDotColor = when {
        device.isBlocked -> blockedColor
        device.isCurrentlyConnected -> activeColor
        else -> ProtonTheme.colors.textHint
    }

    Card(
        shape = RoundedCornerShape(16.dp),
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
            // Device header row: Icon, Name/IP, Block button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Device Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                device.isBlocked -> blockedColor.copy(alpha = 0.15f)
                                device.isCurrentlyConnected -> activeColor.copy(alpha = 0.15f)
                                else -> ProtonTheme.colors.backgroundNorm
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_device_client),
                        contentDescription = null,
                        tint = when {
                            device.isBlocked -> blockedColor
                            device.isCurrentlyConnected -> activeColor
                            else -> inactiveColor
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = device.displayName,
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable { onRename() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit_port),
                                contentDescription = "Rename",
                                tint = ProtonTheme.colors.brandNorm,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Text(
                            text = device.ip,
                            style = ProtonTheme.typography.captionNorm,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = ProtonTheme.colors.textWeak
                        )
                        Text(
                            text = when {
                                device.isBlocked -> "• Blocked"
                                device.isCurrentlyConnected -> "• Active"
                                else -> "• Offline"
                            },
                            style = ProtonTheme.typography.captionNorm,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusDotColor
                        )
                    }
                }

                // Block / Unblock Action Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (device.isBlocked) blockedColor.copy(alpha = 0.15f)
                            else ProtonTheme.colors.backgroundNorm
                        )
                        .clickable { onToggleBlock() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_block_device),
                            contentDescription = null,
                            tint = if (device.isBlocked) blockedColor else ProtonTheme.colors.textWeak,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (device.isBlocked) {
                                stringResource(id = R.string.settings_proxy_sharing_device_unblock_action)
                            } else {
                                stringResource(id = R.string.settings_proxy_sharing_device_block_action)
                            },
                            style = ProtonTheme.typography.captionNorm,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (device.isBlocked) blockedColor else ProtonTheme.colors.textNorm
                        )
                    }
                }
            }

            // Connection Duration & Traffic Stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProtonTheme.colors.backgroundNorm)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Duration
                Column {
                    Text(
                        text = stringResource(id = R.string.settings_proxy_sharing_device_duration),
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 10.sp,
                        color = ProtonTheme.colors.textWeak
                    )
                    val durationText = if (device.isCurrentlyConnected) {
                        val durationMs = (System.currentTimeMillis() - device.firstConnectedTimeMs).coerceAtLeast(0L)
                        formatDuration(durationMs)
                    } else {
                        val elapsedMs = (System.currentTimeMillis() - device.lastActiveTimeMs).coerceAtLeast(0L)
                        "${formatDuration(elapsedMs)} ago"
                    }
                    Text(
                        text = durationText,
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Downloaded
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Downloaded",
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 10.sp,
                        color = ProtonTheme.colors.textWeak
                    )
                    Text(
                        text = formatToMB(device.bytesDownloaded),
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Uploaded
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Uploaded",
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 10.sp,
                        color = ProtonTheme.colors.textWeak
                    )
                    Text(
                        text = formatToMB(device.bytesUploaded),
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Total
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total",
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 10.sp,
                        color = ProtonTheme.colors.textWeak
                    )
                    Text(
                        text = formatToMB(device.totalBytesTransferred),
                        style = ProtonTheme.typography.captionNorm,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ProtonTheme.colors.brandNorm
                    )
                }
            }

            // Daily Quota / Limit Progress Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ProtonTheme.colors.backgroundNorm)
                    .clickable { onSetDailyLimit() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_proxy_sharing_device_daily_limit),
                            style = ProtonTheme.typography.captionNorm,
                            fontSize = 11.sp,
                            color = ProtonTheme.colors.textWeak
                        )

                        Text(
                            text = if (device.dailyLimitBytes > 0L) {
                                "${formatToMB(device.dailyBytesTransferred)} / ${formatToMB(device.dailyLimitBytes)}"
                            } else {
                                stringResource(id = R.string.settings_proxy_sharing_device_no_limit)
                            },
                            style = ProtonTheme.typography.captionNorm,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (device.isLimitExceeded) blockedColor else ProtonTheme.colors.textNorm
                        )
                    }

                    if (device.dailyLimitBytes > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val progress = (device.dailyBytesTransferred.toFloat() / device.dailyLimitBytes.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (device.isLimitExceeded) blockedColor else ProtonTheme.colors.brandNorm,
                            trackColor = ProtonTheme.colors.backgroundSecondary
                        )

                        if (device.isLimitExceeded) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.settings_proxy_sharing_device_limit_exceeded),
                                color = blockedColor,
                                style = ProtonTheme.typography.captionNorm,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Edit limit button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ProtonTheme.colors.backgroundSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_data_limit),
                        contentDescription = "Set Daily Limit",
                        tint = ProtonTheme.colors.brandNorm,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDeviceDialog(
    device: ConnectedDevice,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var textValue by remember { mutableStateOf(device.customName ?: "") }

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
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ProtonTheme.colors.brandNorm.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_edit_port),
                        contentDescription = null,
                        tint = ProtonTheme.colors.brandNorm,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(id = R.string.settings_proxy_sharing_device_rename_dialog_title),
                    style = ProtonTheme.typography.headlineSmall,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtonTheme.colors.textNorm,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Assign a recognizable name for ${device.ip}",
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.textWeak,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "e.g. My Laptop, Living Room TV",
                            style = ProtonTheme.typography.captionNorm,
                            color = ProtonTheme.colors.textHint
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ProtonTheme.colors.backgroundNorm)
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = ProtonTheme.typography.defaultNorm,
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
                                onSave(textValue)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save Name",
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetDailyLimitDialog(
    device: ConnectedDevice,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    val currentLimitMb = if (device.dailyLimitBytes > 0L) (device.dailyLimitBytes / (1024L * 1024L)).toString() else ""
    var customMbText by remember { mutableStateOf(currentLimitMb) }
    var selectedLimitBytes by remember { mutableStateOf(device.dailyLimitBytes) }

    val presets = listOf(
        Pair("No Limit", 0L),
        Pair("100 MB", 100L * 1024L * 1024L),
        Pair("500 MB", 500L * 1024L * 1024L),
        Pair("1 GB", 1024L * 1024L * 1024L),
        Pair("2 GB", 2048L * 1024L * 1024L),
        Pair("5 GB", 5120L * 1024L * 1024L)
    )

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
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ProtonTheme.colors.brandNorm.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_data_limit),
                        contentDescription = null,
                        tint = ProtonTheme.colors.brandNorm,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(id = R.string.settings_proxy_sharing_device_limit_dialog_title),
                    style = ProtonTheme.typography.headlineSmall,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProtonTheme.colors.textNorm,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = stringResource(id = R.string.settings_proxy_sharing_device_limit_dialog_desc),
                    style = ProtonTheme.typography.captionNorm,
                    color = ProtonTheme.colors.textWeak,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Presets grid (2 columns)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { (label, bytes) ->
                                val isSelected = (selectedLimitBytes == bytes && customMbText.isBlank()) ||
                                        (bytes > 0L && customMbText.toLongOrNull()?.let { it * 1024L * 1024L == bytes } == true) ||
                                        (bytes == 0L && selectedLimitBytes == 0L)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) ProtonTheme.colors.brandNorm.copy(alpha = 0.2f)
                                            else ProtonTheme.colors.backgroundNorm
                                        )
                                        .clickable {
                                            selectedLimitBytes = bytes
                                            customMbText = if (bytes > 0L) (bytes / (1024L * 1024L)).toString() else ""
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = ProtonTheme.typography.captionNorm,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) ProtonTheme.colors.brandNorm else ProtonTheme.colors.textNorm
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Custom limit in MB
                OutlinedTextField(
                    value = customMbText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(7)
                        customMbText = digits
                        val mb = digits.toLongOrNull() ?: 0L
                        selectedLimitBytes = mb * 1024L * 1024L
                    },
                    label = { Text("Custom Limit (MB)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ProtonTheme.colors.textNorm
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ProtonTheme.colors.backgroundNorm)
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            style = ProtonTheme.typography.defaultNorm,
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
                                onSave(selectedLimitBytes)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Save Limit",
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}


