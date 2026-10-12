/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.redesign.pingtest

import com.protonvpn.android.base.ui.nav.SafeNavGraphBuilder
import com.protonvpn.android.base.ui.nav.ScreenNoArg
import com.protonvpn.android.base.ui.nav.addToGraph
import com.protonvpn.android.redesign.main_screen.ui.nav.MainNav

object PingTestScreen : ScreenNoArg<MainNav>("pingTest") {

    fun SafeNavGraphBuilder<MainNav>.pingTest() = addToGraph(this) {
        PingTestScreen()
    }
}
