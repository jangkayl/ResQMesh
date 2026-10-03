package com.example.testresqmesh.feature.home.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.location.LocationStatus
import com.example.testresqmesh.core.ui.theme.ModernMint
import com.example.testresqmesh.core.ui.theme.ResQTheme

@Composable
internal fun ModernGpsStatusCard(locationStatus: LocationStatus) {
    val (statusColor, titleText, descText) = when (locationStatus) {
        LocationStatus.ACQUIRING -> Triple(ResQTheme.colors.warning, "Acquiring GPS location...", "Looking for satellite signal for emergency SOS.")
        LocationStatus.READY -> Triple(ModernMint, "Location found", "A last known location is available. SOS will request an update.")
        LocationStatus.ERROR_DENIED -> Triple(MaterialTheme.colorScheme.error, "Location Permission Needed", "Enable location permissions so rescuers can find you.")
        LocationStatus.ERROR_DISABLED -> Triple(ResQTheme.colors.warning, "Phone GPS is Disabled", "Turn on GPS in device settings for SOS positioning.")
        LocationStatus.IDLE -> Triple(MaterialTheme.colorScheme.onSurfaceVariant, "Location Idle", "Position will be acquired when sending SOS.")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (locationStatus == LocationStatus.ACQUIRING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.NearMe,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Clean, reassuring safety guidance footer.
 */
