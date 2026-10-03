package com.example.testresqmesh.feature.radar.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.feedback.ResQEmptyState
import com.example.testresqmesh.core.ui.theme.Spacing

@Composable
internal fun NetworkTopology(topology: Map<String, Set<String>>, onBack: () -> Unit) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF141926),
                border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF242E44)),
                onClick = onBack
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Network Topology",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Active multi-hop routes discovered across the mesh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (topology.isEmpty()) {
            ResQEmptyState(
                "No Routes Recorded",
                "Mesh links automatically generate when multiple ResQMesh peers exchange packets.",
                icon = Icons.Outlined.Hub
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                topology.forEach { (from, to) ->
                    item(key = from) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = if (isLight) MaterialTheme.colorScheme.surface else Color(0xFF111520),
                            border = BorderStroke(1.dp, if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF1F283C))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Hub,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = from,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                if (to.isEmpty()) {
                                    Text(
                                        text = "No outward peer connections",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    to.forEach { destination ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                                contentDescription = null,
                                                tint = if (isLight) Color(0xFF00838F) else Color(0xFF00E5FF),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = destination,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = if (isLight) Color(0xFF00838F) else Color(0xFF00E5FF),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A current topology hint, deliberately not a promise of the next delivery route. */
