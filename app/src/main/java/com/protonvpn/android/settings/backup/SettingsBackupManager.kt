/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.settings.backup

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.profiles.data.ProfileColor
import com.protonvpn.android.profiles.data.ProfileEntity
import com.protonvpn.android.profiles.data.ProfileIcon
import com.protonvpn.android.profiles.data.ProfilesDao
import com.protonvpn.android.proxysharing.ProxySharingPreferences
import com.protonvpn.android.redesign.recents.data.ConnectIntentData
import com.protonvpn.android.redesign.recents.data.toData
import com.protonvpn.android.settings.data.CurrentUserLocalSettingsManager
import com.protonvpn.android.settings.data.LocalUserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ProfileBackupItem(
    val name: String,
    val colorName: String,
    val iconName: String,
    val connectIntentData: ConnectIntentData,
    val autoOpenText: String = "",
    val autoOpenEnabled: Boolean = false,
    val autoOpenUrlPrivately: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ProxySharingBackup(
    val socksPort: Int = 10808,
    val httpPort: Int = 10809,
    val pacPort: Int = 10810,
    val isSocksEnabled: Boolean = true,
    val isHttpEnabled: Boolean = true,
    val isPacEnabled: Boolean = true,
    val bypassIrEnabled: Boolean = false,
    val customBypassUrl: String? = null
)

@Serializable
data class SettingsBackupData(
    val version: Int = 1,
    val appVersion: String = "5.6.0.1",
    val backupTimestamp: Long = System.currentTimeMillis(),
    val settings: LocalUserSettings? = null,
    val profiles: List<ProfileBackupItem> = emptyList(),
    val proxySharing: ProxySharingBackup? = null
)

data class RestoreResult(
    val profilesCount: Int,
    val hasSettings: Boolean
)

@Singleton
class SettingsBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val currentUser: CurrentUser,
    private val currentUserLocalSettingsManager: CurrentUserLocalSettingsManager,
    private val profilesDao: ProfilesDao,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackupJson(): String {
        val currentSettings = currentUserLocalSettingsManager.rawCurrentUserSettingsFlow.first()
        val user = currentUser.user()
        val currentProfiles = if (user != null) {
            profilesDao.getProfiles(user.userId).firstOrNull() ?: emptyList()
        } else {
            emptyList()
        }

        val proxyPrefs = ProxySharingPreferences(context)
        val proxySharingBackup = ProxySharingBackup(
            socksPort = proxyPrefs.socksPort,
            httpPort = proxyPrefs.httpPort,
            pacPort = proxyPrefs.pacPort,
            isSocksEnabled = proxyPrefs.isSocksEnabled,
            isHttpEnabled = proxyPrefs.isHttpEnabled,
            isPacEnabled = proxyPrefs.isPacEnabled,
            bypassIrEnabled = proxyPrefs.bypassIrEnabled,
            customBypassUrl = proxyPrefs.customBypassUrl
        )

        val profileItems = currentProfiles.map { profile ->
            ProfileBackupItem(
                name = profile.info.name,
                colorName = profile.info.color.name,
                iconName = profile.info.icon.name,
                connectIntentData = profile.connectIntent.toData(),
                autoOpenText = when (val ao = profile.autoOpen) {
                    is com.protonvpn.android.profiles.data.ProfileAutoOpen.None -> ""
                    is com.protonvpn.android.profiles.data.ProfileAutoOpen.App -> "app:${ao.packageName}"
                    is com.protonvpn.android.profiles.data.ProfileAutoOpen.Url -> ao.url.toString()
                },
                autoOpenEnabled = profile.autoOpen !is com.protonvpn.android.profiles.data.ProfileAutoOpen.None,
                autoOpenUrlPrivately = (profile.autoOpen as? com.protonvpn.android.profiles.data.ProfileAutoOpen.Url)?.openInPrivateMode ?: false,
                createdAt = profile.info.createdAt
            )
        }

        val backupData = SettingsBackupData(
            version = 1,
            appVersion = "5.6.0.1",
            backupTimestamp = System.currentTimeMillis(),
            settings = currentSettings,
            profiles = profileItems,
            proxySharing = proxySharingBackup
        )

        return json.encodeToString(backupData)
    }

    suspend fun restoreFromJson(jsonString: String): Result<RestoreResult> {
        return try {
            val backupData = json.decodeFromString<SettingsBackupData>(jsonString)
            var restoredProfilesCount = 0

            // 1. Restore settings
            if (backupData.settings != null) {
                currentUserLocalSettingsManager.update { backupData.settings }
                ProtonLogger.logCustom(LogCategory.APP, "Local settings restored from backup")
            }

            // 2. Restore proxy sharing settings
            if (backupData.proxySharing != null) {
                val p = ProxySharingPreferences(context)
                p.socksPort = backupData.proxySharing.socksPort
                p.httpPort = backupData.proxySharing.httpPort
                p.pacPort = backupData.proxySharing.pacPort
                p.isSocksEnabled = backupData.proxySharing.isSocksEnabled
                p.isHttpEnabled = backupData.proxySharing.isHttpEnabled
                p.isPacEnabled = backupData.proxySharing.isPacEnabled
                p.bypassIrEnabled = backupData.proxySharing.bypassIrEnabled
                p.customBypassUrl = backupData.proxySharing.customBypassUrl
                ProtonLogger.logCustom(LogCategory.APP, "Proxy sharing settings restored from backup")
            }

            // 3. Restore profile servers
            val user = currentUser.user()
            if (user != null && backupData.profiles.isNotEmpty()) {
                val now = System.currentTimeMillis()
                backupData.profiles.forEachIndexed { index, item ->
                    val color = try { ProfileColor.valueOf(item.colorName) } catch (_: Exception) { ProfileColor.Color1 }
                    val icon = try { ProfileIcon.valueOf(item.iconName) } catch (_: Exception) { ProfileIcon.Icon1 }
                    val profileId = item.connectIntentData.profileId ?: (now + index)
                    val intentWithId = item.connectIntentData.copy(profileId = profileId)

                    val entity = ProfileEntity(
                        userId = user.userId,
                        name = item.name,
                        color = color,
                        icon = icon,
                        autoOpenText = item.autoOpenText,
                        autoOpenEnabled = item.autoOpenEnabled,
                        autoOpenUrlPrivately = item.autoOpenUrlPrivately,
                        createdAt = if (item.createdAt > 0) item.createdAt else now,
                        lastConnectedAt = null,
                        isUserCreated = true,
                        connectIntentData = intentWithId
                    )
                    profilesDao.upsert(entity)
                    restoredProfilesCount++
                }
                ProtonLogger.logCustom(LogCategory.APP, "Restored $restoredProfilesCount profile servers from backup")
            }

            Result.success(RestoreResult(profilesCount = restoredProfilesCount, hasSettings = backupData.settings != null))
        } catch (e: Exception) {
            ProtonLogger.logCustom(LogCategory.APP, "Failed to restore backup: ${e.message}")
            Result.failure(e)
        }
    }

    fun shareBackup(activity: Activity, jsonContent: String) {
        try {
            val fileName = "protonvpn-backup-${System.currentTimeMillis()}.json"
            val cacheFile = File(activity.cacheDir, fileName).apply {
                writeText(jsonContent)
            }
            val uri: Uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.provider",
                cacheFile
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Proton VPN Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(Intent.createChooser(intent, "Export Backup"))
        } catch (_: Exception) {
            // Fallback to text send
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, jsonContent)
                putExtra(Intent.EXTRA_SUBJECT, "Proton VPN Backup")
            }
            activity.startActivity(Intent.createChooser(textIntent, "Export Backup"))
        }
    }

    fun copyToClipboard(jsonContent: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ProtonVPN Backup", jsonContent)
        clipboard.setPrimaryClip(clip)
    }
}
