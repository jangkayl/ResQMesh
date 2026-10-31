package edu.cit.resqmesh.feature.sos.ui.map

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.ui.theme.ResQSize
import edu.cit.resqmesh.core.ui.theme.SafetyOrange
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.sos.ui.SosMapViewState
import edu.cit.resqmesh.feature.sos.ui.mapLocationTitle
import edu.cit.resqmesh.feature.sos.ui.mapSentTime

@Composable
internal fun MapExpandedDetails(
    state: SosMapViewState.MapReady,
    alertMessage: ChatMessage,
    onShowOnMap: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        DetailRow(label = "Location", value = "${state.formattedLat}, ${state.formattedLng}")
        mapSentTime(alertMessage)?.let { DetailRow(label = "Sent at", value = it) }

        Button(
            onClick = onShowOnMap,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ResQSize.MinimumTouchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.Small))
            Text(text = "Show on map", fontWeight = FontWeight.Bold)
        }

        MapLocationActions(
            lat = state.lat,
            lng = state.lng,
            label = "${mapLocationTitle(alertMessage)}: ${alertMessage.senderName}"
        )

        // OSM / Source Attribution
        Text(
            text = state.attribution,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
