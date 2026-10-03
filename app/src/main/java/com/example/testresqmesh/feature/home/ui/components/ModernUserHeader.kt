package com.example.testresqmesh.feature.home.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.ui.theme.ModernSky
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.feature.home.model.HomeNetworkSummary

@Composable
internal fun ModernUserHeader(
    myDeviceName: String,
    isNodeActive: Boolean,
    summary: HomeNetworkSummary,
    connectionStatus: String,
    onProfileClick: () -> Unit
) {
    val displayName = NodeIdentity.displayNameOf(myDeviceName)
    val nodeId = remember(myDeviceName) {
        if (myDeviceName.contains('#')) "#" + myDeviceName.substringAfter('#') else ""
    }
    val initial = displayName.firstOrNull()?.uppercase() ?: "U"

    val defaultTitle = stringResource(if (isNodeActive) R.string.home_ready_title else R.string.home_setup_title)
    val readinessTitle = when (connectionStatus) {
        com.example.testresqmesh.core.network.bluetooth.MeshTransportState.BLUETOOTH_OFF.status -> "Bluetooth off"
        com.example.testresqmesh.core.network.bluetooth.MeshTransportState.PERMISSION_REQUIRED.status -> "Permission needed"
        com.example.testresqmesh.core.network.bluetooth.MeshTransportState.STARTING.status -> "Starting mesh"
        com.example.testresqmesh.core.network.bluetooth.MeshTransportState.SEARCHING.status -> "Searching"
        com.example.testresqmesh.core.network.bluetooth.MeshTransportState.ERROR.status -> "Recovery pending"
        else -> defaultTitle
    }
    val meshStatusColor = if (isNodeActive) {
        if (summary.directPeers > 0) ResQTheme.colors.success else ModernSky
    } else {
        ResQTheme.colors.warning
    }

    val infiniteTransition = rememberInfiniteTransition(label = "BeaconPulse")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    // Profile & Presence Row
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // User Avatar Circle
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onProfileClick),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                shadowElevation = 3.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Hello, $displayName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (nodeId.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = nodeId,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                // Live Mesh Presence Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = meshStatusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, meshStatusColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .graphicsLayer { alpha = if (isNodeActive) beaconAlpha else 0.4f }
                                .clip(CircleShape)
                                .background(meshStatusColor)
                        )
                        Text(
                            text = readinessTitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = meshStatusColor
                        )
                    }
                }
            }
        }

        // Profile Button
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 2.dp,
            onClick = onProfileClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PersonOutline,
                    contentDescription = stringResource(R.string.home_profile_action),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Modern "Nearby Mesh Friends" horizontal stories/presence bar.
 */
