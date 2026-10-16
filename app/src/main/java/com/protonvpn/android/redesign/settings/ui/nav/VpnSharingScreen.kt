/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.settings.ui.nav

import com.protonvpn.android.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.base.ui.nav.ScreenNoArg
import com.protonvpn.android.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.main_screen.ui.nav.MainNav
import com.protonvpn.android.redesign.settings.ui.ProxySharingSetting

object VpnSharingScreen : ScreenNoArg<MainNav>("vpnSharing") {

    fun SafeNavGraphBuilder<MainNav>.vpnSharing() = addToGraph(this) {
        ProxySharingSetting()
    }
}
