package com.example.testresqmesh.feature.home.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ModernMint
import com.example.testresqmesh.core.ui.theme.ResQTheme

@Composable
internal fun CommunityIncidentsBanner(
    activeIncidentCount: Int,
    criticalIncidentCount: Int,
    onIncidentsClick: () -> Unit
) {
    val hasActive = activeIncidentCount > 0
    val isCritical = criticalIncidentCount > 0

    val bannerBg = if (hasActive) {
        ResQTheme.colors.sosContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val bannerBorderColor = if (hasActive) {
        if (isCritical) ResQTheme.colors.sos else ResQTheme.colors.sos.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    }
    val iconBg = if (hasActive) {
        ResQTheme.colors.sos
    } else {
        ModernMint.copy(alpha = 0.12f)
    }
    val iconTint = if (hasActive) {
        Color.White
    } else {
        ModernMint
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onIncidentsClick),
        shape = RoundedCornerShape(18.dp),
        color = bannerBg,
        border = BorderStroke(
            width = if (isCritical) 1.5.dp else 1.dp,
            color = bannerBorderColor
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconBg,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (hasActive) Icons.Outlined.WarningAmber else Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "EMERGENCY INCIDENTS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (hasActive) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ResQTheme.colors.sos
                            ) {
                                Text(
                                    text = if (isCritical) "URGENT" else "$activeIncidentCount ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Black),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (hasActive) "$activeIncidentCount emergency request${if (activeIncidentCount > 1) "s" else ""} nearby. Tap to triage." else "Community Mutual Aid • All clear in your area.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Modern Mesh Topology Summary Card ("Your network") with expandable 2D radar.
 */
