/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.vpn.pause

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPauseManagerTest {

    @Test
    fun pauseDataCalculatesRemainingSecondsCorrectly() {
        val now = 1000000L
        val duration = 15 * 60 * 1000L // 15 minutes
        val deadline = now + duration

        val data = VpnPauseData(
            isPaused = true,
            deadlineMs = deadline,
            durationMs = duration,
            serverName = "CH#1"
        )

        assertTrue(data.isPaused)
        assertEquals(deadline, data.deadlineMs)

        val remainingMs = (deadline - now).coerceAtLeast(0L)
        val remainingSeconds = (remainingMs + 999) / 1000
        assertEquals(15 * 60L, remainingSeconds)

        // Half expired
        val later = now + (7 * 60 * 1000L)
        val remainingMsLater = (deadline - later).coerceAtLeast(0L)
        val remainingSecondsLater = (remainingMsLater + 999) / 1000
        assertEquals(8 * 60L, remainingSecondsLater)

        // Fully expired
        val pastDeadline = deadline + 1000L
        val remainingExpired = ((deadline - pastDeadline).coerceAtLeast(0L) + 999) / 1000
        assertEquals(0L, remainingExpired)
    }

    @Test
    fun pauseStateDefaultIsUnpaused() {
        val state = VpnPauseState()
        assertFalse(state.isPaused)
        assertEquals(0L, state.remainingSeconds)
        assertEquals(0L, state.deadlineMs)
    }
}
