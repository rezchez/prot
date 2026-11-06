package com.protonvpn.android.vpn.wireguard

/*
 * Copyright (c) 2021 Proton AG
 *
 * This file is part of ProtonVPN.
 *
 * ProtonVPN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import android.content.Context
import com.protonvpn.android.auth.usecase.CurrentUser
import com.protonvpn.android.concurrency.VpnDispatcherProvider
import com.protonvpn.android.logging.ConnError
import com.protonvpn.android.logging.LogCategory
import com.protonvpn.android.logging.ProtonLogger
import com.protonvpn.android.models.config.TransmissionProtocol
import com.protonvpn.android.models.config.VpnProtocol
import com.protonvpn.android.models.vpn.ConnectionParams
import com.protonvpn.android.models.vpn.ConnectionParamsWireguard
import com.protonvpn.android.models.vpn.usecase.ComputeAllowedIPs
import com.protonvpn.android.redesign.vpn.AnyConnectIntent
import com.protonvpn.android.redesign.vpn.usecases.SettingsForConnection
import com.protonvpn.android.servers.Server
import com.protonvpn.android.ui.ForegroundActivityTracker
import com.protonvpn.android.ui.home.GetNetZone
import com.protonvpn.android.vpn.CertificateRepository
import com.protonvpn.android.vpn.ErrorType
import com.protonvpn.android.vpn.LocalAgentUnreachableTracker
import com.protonvpn.android.vpn.NetworkCapabilitiesFlow
import com.protonvpn.android.vpn.PrepareForConnection
import com.protonvpn.android.vpn.PrepareResult
import com.protonvpn.android.vpn.VpnBackend
import com.protonvpn.android.vpn.VpnState
import com.protonvpn.android.vpn.alwayson.VpnAlwaysOn
import com.protonvpn.android.vpn.alwayson.VpnAlwaysOnStorage
import com.protonvpn.android.vpn.protun.toLegacyState
import com.protonvpn.android.vpn.usecases.ServerNameTopStrategyEnabled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import me.proton.core.network.data.di.SharedOkHttpClient
import me.proton.core.network.domain.NetworkManager
import me.proton.vpn.core.api.ProtonVpnCore
import me.proton.vpn.core.api.SniStrategy
import okhttp3.OkHttpClient
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WireguardBackend @Inject constructor(
    @ApplicationContext val context: Context,
    networkManager: NetworkManager,
    networkCapabilitiesFlow: NetworkCapabilitiesFlow,
    settingsForConnection: SettingsForConnection,
    certificateRepository: CertificateRepository,
    dispatcherProvider: VpnDispatcherProvider,
    mainScope: CoroutineScope,
    localAgentUnreachableTracker: LocalAgentUnreachableTracker,
    currentUser: CurrentUser,
    getNetZone: GetNetZone,
    private val prepareForConnection: PrepareForConnection,
    private val computeAllowedIPs: ComputeAllowedIPs,
    foregroundActivityTracker: ForegroundActivityTracker,
    @SharedOkHttpClient okHttp: OkHttpClient,
    private val vpnCore: ProtonVpnCore,
    private val vpnAlwaysOnStorage: VpnAlwaysOnStorage,
    private val serverNameTopStrategyEnabled: ServerNameTopStrategyEnabled,
) : VpnBackend(
    settingsForConnection, certificateRepository, networkManager, networkCapabilitiesFlow, VpnProtocol.WireGuard, mainScope,
    dispatcherProvider, localAgentUnreachableTracker, currentUser, getNetZone, foregroundActivityTracker, okHttp
) {
    @Volatile private var ownsRustConnection = false

    init {
        vpnCore.connectionManager.state.onEach { state ->
            if (ownsRustConnection) {
                ProtonLogger.logCustom(LogCategory.CONN_WIREGUARD, "Rust WireGuard state changed: $state")
                vpnProtocolState = state.connectionState.toLegacyState(inV1mode = true)
                val alwaysOn = state.alwaysOn
                val lockdown = state.isLockdownEnabled
                if (alwaysOn != null && lockdown != null) {
                    vpnAlwaysOnStorage.setVpnAlwaysOn(VpnAlwaysOn(alwaysOn, lockdown))
                }
            }
        }.launchIn(mainScope)
    }

    override suspend fun prepareForConnection(
        connectIntent: AnyConnectIntent,
        server: Server,
        transmissionProtocols: Set<TransmissionProtocol>,
        scan: Boolean,
        numberOfPorts: Int,
        waitForAll: Boolean
    ): List<PrepareResult> {
        val protocolInfo = prepareForConnection.prepare(server, vpnProtocol, transmissionProtocols, scan,
            numberOfPorts, waitForAll, PRIMARY_PORT, includeTls = true)
        return protocolInfo.map {
            PrepareResult(
                this,
                ConnectionParamsWireguard(
                    connectIntent,
                    server,
                    it.port,
                    it.connectingDomain,
                    it.entryIp,
                    it.transmissionProtocol,
                    settingsForConnection.getFor(connectIntent).ipV6Enabled
                )
            )
        }
    }

    override suspend fun connect(connectionParams: ConnectionParams) {
        super.connect(connectionParams)
        ownsRustConnection = true
        vpnProtocolState = VpnState.Connecting
        val wireguardParams = connectionParams as ConnectionParamsWireguard
        try {
            val settings = settingsForConnection.getFor(wireguardParams.connectIntent)
            val sniStrategy = if (serverNameTopStrategyEnabled())
                SniStrategy.Top else SniStrategy.Random
            val config = wireguardParams.getTunnelConfig(
                context, settings, currentUser.sessionId(), certificateRepository, computeAllowedIPs, sniStrategy
            )
            vpnCore.connectionManager.connect(config)
        } catch (e: SecurityException) {
            ownsRustConnection = false
            if (e.message?.contains("INTERACT_ACROSS_USERS") == true)
                selfStateFlow.value = VpnState.Error(ErrorType.MULTI_USER_PERMISSION, isFinal = true)
            else
                handleConnectException(e)
        } catch (e: Exception) {
            if (e is CancellationException) {
                ownsRustConnection = false
                throw e
            }
            ownsRustConnection = false
            handleConnectException(e)
        }
    }

    override suspend fun closeVpnTunnel(withStateChange: Boolean) {
        ownsRustConnection = false
        vpnCore.connectionManager.disconnect()
        if (withStateChange) {
            // Give the app time to close the notification before the VPN service stops.
            vpnProtocolState = VpnState.Disabled
            delay(10)
        }
    }

    private fun handleConnectException(e: Exception) {
        ProtonLogger.log(
            ConnError,
            "Caught exception while connecting with Rust WireGuard: $e\n" +
                StringWriter().apply { e.printStackTrace(PrintWriter(this)) }.toString()
        )
        selfStateFlow.value = VpnState.Error(ErrorType.GENERIC_ERROR, isFinal = true)
    }

    companion object {
        private const val PRIMARY_PORT = 443
    }
}
