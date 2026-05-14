package com.example.testresqmesh.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.ui.components.buttons.ResQButton
import com.example.testresqmesh.ui.theme.Spacing

@Composable
fun PermissionsScreen(onAllGranted: () -> Unit) {
    val pinkBackground = Color(0xFFFEE2E2) // Light pink from mockup

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pinkBackground)
            .padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Handle bar at the top (bottom sheet style)
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.1f))
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "No Signal? No Problem.",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(Spacing.Small))

        Text(
            text = "ResQMesh needs hardware access to build your local mesh network.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = Color.Black.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        PermissionCard(
            title = "Bluetooth Radio",
            description = "Discovers and connects to nearby ResQMesh nodes without needing internet or cell service.",
            icon = Icons.Default.Bluetooth,
            iconColor = Color(0xFF3B82F6)
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        PermissionCard(
            title = "Location Services",
            description = "Allows the Radar to plot your relative position to responders. GPS is used exclusively for mesh mapping.",
            icon = Icons.Default.LocationOn,
            iconColor = Color(0xFF10B981)
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        PermissionCard(
            title = "Local Network",
            description = "Uses WiFi Direct to relay large packets and broadcasts between distant nodes in the network.",
            icon = Icons.Default.Wifi,
            iconColor = Color(0xFFF59E0B)
        )

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(
                    "End-to-end encrypted. Data stays local and never touches the cloud.",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        ResQButton(
            onClick = onAllGranted,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(Spacing.Medium)
        ) {
            Text("Grant Hardware Access")
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = iconColor.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.width(Spacing.Medium))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
                Surface(
                    color = Color.Black.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Required",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 8.sp,
                        color = Color.Black.copy(alpha = 0.4f)
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black.copy(alpha = 0.6f)
            )
        }
        
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.Black.copy(alpha = 0.2f)
        )
    }
}
