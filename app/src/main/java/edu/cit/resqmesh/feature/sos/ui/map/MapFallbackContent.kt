package edu.cit.resqmesh.feature.sos.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.ui.theme.SafetyOrange
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.sos.ui.SosMapViewState
import edu.cit.resqmesh.feature.sos.ui.mapLocationTitle
import edu.cit.resqmesh.feature.sos.ui.mapSentTime

@Composable
internal fun MaplessSosFallback(
    alertMessage: ChatMessage,
    state: SosMapViewState.MaplessFallback,
    onInstallMap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Map Status Card. A missing map is not an emergency, so it stays in the normal theme colors.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        tint = SafetyOrange,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(Spacing.Medium))
                    Column {
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.showInstallAction) {
                                "You can still see the location below. " +
                                    "Download the map once and it works without internet."
                            } else {
                                "You can still see the location below."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.showInstallAction) {
                    Button(
                        onClick = onInstallMap,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(Spacing.Small))
                        Text("Download map", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Location Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${alertMessage.senderName}'s location",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.Medium))

                // High-Vis Coordinate Display
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.Medium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.formattedLat,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.formattedLng,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Medium))

                MapLocationActions(
                    lat = state.lat,
                    lng = state.lng,
                    label = "${mapLocationTitle(alertMessage)}: ${alertMessage.senderName}"
                )

                Spacer(Modifier.height(Spacing.Medium))

                DetailRow(label = "From", value = alertMessage.senderName)
                mapSentTime(alertMessage)?.let { DetailRow(label = "Sent at", value = it) }
                if (alertMessage.text.isNotBlank()) {
                    DetailRow(label = "Message", value = alertMessage.text)
                }
            }
        }
    }
}
