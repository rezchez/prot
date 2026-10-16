/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.settings.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.SimpleTopAppBar
import com.protonvpn.android.base.ui.TopAppBarBackIcon
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import com.protonvpn.android.settings.backup.SettingsBackupManager
import kotlinx.coroutines.launch
import me.proton.core.compose.theme.ProtonTheme
import me.proton.core.compose.theme.captionNorm
import me.proton.core.compose.theme.defaultNorm
import me.proton.core.compose.theme.defaultSmallWeak
import me.proton.core.compose.theme.headlineSmall
import me.proton.core.presentation.R as CoreR

@Composable
fun BackupRestoreScreen(
    backupManager: SettingsBackupManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var jsonInputText by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccessStatus by remember { mutableStateOf(true) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    pendingRestoreJson = content
                    showConfirmDialog = true
                } else {
                    statusMessage = "Selected file is empty."
                    isSuccessStatus = false
                }
            } catch (e: Exception) {
                statusMessage = "Error reading backup file: ${e.message}"
                isSuccessStatus = false
            }
        }
    }

    if (showConfirmDialog && pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                pendingRestoreJson = null
            },
            title = {
                Text(
                    text = stringResource(R.string.backup_restore_title),
                    style = ProtonTheme.typography.headlineSmall,
                    color = ProtonTheme.colors.textNorm
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to restore? This will apply the backed-up settings and import all profile servers.",
                    style = ProtonTheme.typography.defaultNorm,
                    color = ProtonTheme.colors.textWeak
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val jsonToRestore = pendingRestoreJson
                        showConfirmDialog = false
                        pendingRestoreJson = null
                        if (jsonToRestore != null) {
                            scope.launch {
                                val result = backupManager.restoreFromJson(jsonToRestore)
                                if (result.isSuccess) {
                                    val count = result.getOrNull()?.profilesCount ?: 0
                                    statusMessage = context.getString(R.string.backup_restore_success, count)
                                    isSuccessStatus = true
                                    Toast.makeText(context, statusMessage, Toast.LENGTH_LONG).show()
                                } else {
                                    statusMessage = context.getString(
                                        R.string.backup_restore_error,
                                        result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                                    )
                                    isSuccessStatus = false
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Restore",
                        color = ProtonTheme.colors.notificationWarning,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        pendingRestoreJson = null
                    }
                ) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ProtonTheme.colors.backgroundNorm)
    ) {
        SimpleTopAppBar(
            title = { Text(stringResource(R.string.settings_backup_restore_title)) },
            navigationIcon = {
                TopAppBarBackIcon(onClick = onClose)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            statusMessage?.let { msg ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccessStatus)
                            Color(0xFF22C55E).copy(alpha = 0.15f)
                        else
                            ProtonTheme.colors.notificationError.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isSuccessStatus) CoreR.drawable.ic_proton_checkmark else R.drawable.ic_exclamation_triangle_filled
                            ),
                            contentDescription = null,
                            tint = if (isSuccessStatus) Color(0xFF22C55E) else ProtonTheme.colors.notificationError,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = msg,
                            style = ProtonTheme.typography.defaultNorm,
                            color = ProtonTheme.colors.textNorm,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- BACKUP SECTION ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ProtonTheme.colors.backgroundSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_proton_arrow_out_square),
                            contentDescription = null,
                            tint = ProtonTheme.colors.brandNorm,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.backup_create_title),
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.Bold,
                            color = ProtonTheme.colors.textNorm
                        )
                    }

                    Text(
                        text = stringResource(R.string.backup_create_desc),
                        style = ProtonTheme.typography.defaultSmallWeak,
                        color = ProtonTheme.colors.textWeak
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VpnSolidButton(
                            text = stringResource(R.string.backup_export_button),
                            onClick = {
                                scope.launch {
                                    val jsonString = backupManager.createBackupJson()
                                    if (activity != null) {
                                        backupManager.shareBackup(activity, jsonString)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        VpnWeakSolidButton(
                            text = stringResource(R.string.backup_copy_button),
                            onClick = {
                                scope.launch {
                                    val jsonString = backupManager.createBackupJson()
                                    backupManager.copyToClipboard(jsonString)
                                    Toast.makeText(context, context.getString(R.string.backup_copied_toast), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // --- RESTORE SECTION ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ProtonTheme.colors.backgroundSecondary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_proton_arrow_rotate_right),
                            contentDescription = null,
                            tint = ProtonTheme.colors.notificationWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.backup_restore_title),
                            style = ProtonTheme.typography.defaultNorm,
                            fontWeight = FontWeight.Bold,
                            color = ProtonTheme.colors.textNorm
                        )
                    }

                    Text(
                        text = stringResource(R.string.backup_restore_desc),
                        style = ProtonTheme.typography.defaultSmallWeak,
                        color = ProtonTheme.colors.textWeak
                    )

                    VpnWeakSolidButton(
                        text = stringResource(R.string.backup_restore_from_file),
                        onClick = {
                            filePickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(4.dp))

                    OutlinedTextField(
                        value = jsonInputText,
                        onValueChange = { jsonInputText = it },
                        label = { Text("Paste Backup JSON") },
                        placeholder = { Text("{\"version\":1, ...}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        maxLines = 4
                    )

                    VpnSolidButton(
                        text = stringResource(R.string.backup_paste_and_restore),
                        onClick = {
                            if (jsonInputText.isNotBlank()) {
                                pendingRestoreJson = jsonInputText.trim()
                                showConfirmDialog = true
                            } else {
                                Toast.makeText(context, "Please paste valid JSON text first", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = jsonInputText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
