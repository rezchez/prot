/*
 * Copyright (c) 2026 Proton AG
 *
 * This file is part of ProtonVPN.
 */

package com.protonvpn.android.vpn.pause

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.protonvpn.android.R
import com.protonvpn.android.base.ui.VpnSolidButton
import com.protonvpn.android.base.ui.VpnWeakSolidButton
import me.proton.core.compose.theme.ProtonTheme
import java.util.Locale

@Composable
fun VpnPausedCard(
    pauseState: VpnPauseState.Paused,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mins = (pauseState.remainingMs / 1000) / 60
    val secs = (pauseState.remainingMs / 1000) % 60
    val formattedTime = String.format(Locale.US, "%02d:%02d", mins, secs)

    val surfaceShape = ProtonTheme.shapes.large
    Surface(
        color = ProtonTheme.colors.backgroundSecondary,
        shape = surfaceShape,
        modifier = modifier.border(1.dp, ProtonTheme.colors.separatorNorm, surfaceShape)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pause),
                    contentDescription = null,
                    tint = ProtonTheme.colors.brandNorm,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.vpn_paused),
                        style = ProtonTheme.typography.body1Medium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.vpn_paused_remaining, formattedTime),
                        style = ProtonTheme.typography.body2Regular,
                        color = ProtonTheme.colors.brandNorm,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            VpnSolidButton(
                text = stringResource(R.string.resume_vpn),
                onClick = onResume,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            VpnWeakSolidButton(
                text = stringResource(R.string.cancel_pause),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
