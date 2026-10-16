/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.protonvpn.android.servers.Server
import com.protonvpn.android.vpn.VpnUiDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PingTestViewModel @Inject constructor(
    private val pingTestManager: PingTestManager
) : ViewModel() {

    val uiState: StateFlow<PingTestUiState> = pingTestManager.state

    fun onAttemptsChanged(input: String) {
        pingTestManager.setAttemptsInput(input)
    }

    fun onStartClicked() {
        pingTestManager.showConfirmDialog(true)
    }

    fun onConfirmDialogDismiss() {
        pingTestManager.showConfirmDialog(false)
    }

    fun onConfirmStart() {
        pingTestManager.startTest()
    }

    fun onCancelClicked() {
        pingTestManager.cancelTest()
    }

    fun onPauseClicked() {
        pingTestManager.pauseTest()
    }

    fun onResumeClicked() {
        pingTestManager.resumeTest()
    }

    fun onShowCountryFilter(show: Boolean) {
        pingTestManager.showCountryFilterSheet(show)
    }

    fun onToggleCountry(countryCode: String) {
        pingTestManager.toggleCountrySelection(countryCode)
    }

    fun onSelectAllCountries() {
        pingTestManager.selectAllCountries()
    }

    fun onAddServerToProfile(server: Server) {
        viewModelScope.launch {
            pingTestManager.addServerToProfile(server)
        }
    }

    fun onTabSelected(tab: PingTestTab) {
        pingTestManager.setSelectedTab(tab)
    }

    fun onConnectToServer(vpnUiDelegate: VpnUiDelegate, server: Server) {
        pingTestManager.connectToServer(vpnUiDelegate, server)
    }
}
