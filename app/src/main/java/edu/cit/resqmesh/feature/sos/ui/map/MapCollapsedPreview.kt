package edu.cit.resqmesh.feature.sos.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.ui.theme.ResQSize
import edu.cit.resqmesh.core.ui.theme.SignalRed

@Composable
internal fun MapCollapsedPreview(
    alertMessage: ChatMessage,
    distance: Pair<String, String>,
    isSheetExpanded: Boolean,
    onToggle: () -> Unit
) {
    // Always visible: how far away the location is.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = if (alertMessage.isSOS) SignalRed else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = distance.first,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (distance.second.isNotEmpty()) {
                Text(
                    text = distance.second,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = { onToggle() },
            modifier = Modifier.size(ResQSize.MinimumTouchTarget)
        ) {
            Icon(
                imageVector = if (isSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = if (isSheetExpanded) "Show less" else "Show more",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
