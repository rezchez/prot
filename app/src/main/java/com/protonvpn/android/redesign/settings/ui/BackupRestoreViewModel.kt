/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.settings.ui

import androidx.lifecycle.ViewModel
import com.protonvpn.android.settings.backup.SettingsBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    val backupManager: SettingsBackupManager
) : ViewModel()
