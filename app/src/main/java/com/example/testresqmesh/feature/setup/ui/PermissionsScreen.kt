package com.example.testresqmesh.feature.setup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.ResQButton
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme

import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

@Composable
fun PermissionsScreen(
    onAllSet: () -> Unit,
    hasPermissions: Boolean,
    requestPermissions: () -> Unit,
    checkHardware: () -> Boolean
) {
    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Local state to track hardware status
    var isHardwareOn by remember { mutableStateOf(checkHardware()) }

    // Re-check hardware when the app is resumed (e.g. after user returns from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isHardwareOn = checkHardware()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Auto-advance if everything is already granted and on
    LaunchedEffect(hasPermissions, isHardwareOn) {
        if (hasPermissions && isHardwareOn) {
            onAllSet()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = if (!hasPermissions) "Grant Access" else "Hardware Check",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(Spacing.Small))

        Text(
            text = if (!hasPermissions) 
                "ResQMesh needs hardware permissions to build your local mesh network." 
                else "Permissions granted! Now please ensure your Bluetooth, GPS, and Wi-Fi are turned ON.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        PermissionCard(
            title = "Bluetooth Radio",
            description = "Discovers and connects to nearby nodes.",
            icon = Icons.Default.Bluetooth,
            iconColor = Color(0xFF3B82F6)
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        PermissionCard(
            title = "Location & GPS",
            description = "Plots relative positions on the Radar.",
            icon = Icons.Default.LocationOn,
            iconColor = Color(0xFF10B981)
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        PermissionCard(
            title = "Wi-Fi Networking",
            description = "Relays high-bandwidth mesh packets.",
            icon = Icons.Default.Wifi,
            iconColor = Color(0xFFF59E0B)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text(
                    "Data stays local and never touches the cloud.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        ResQButton(
            onClick = {
                if (!hasPermissions) {
                    requestPermissions()
                } else {
                    isHardwareOn = checkHardware()
                    if (isHardwareOn) {
                        onAllSet()
                    } else {
                        // Optional: can open system settings here
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            content = {
                Text(
                    text = if (!hasPermissions) "Grant Hardware Access" 
                           else if (!isHardwareOn) "Check Hardware Again" 
                           else "All Set! Continue",
                    fontWeight = FontWeight.Bold
                )
            }
        
        if (hasPermissions && !isHardwareOn) {
            Text(
                text = "Please enable Bluetooth, GPS, and Wi-Fi in your system settings to proceed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
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
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(Spacing.Small))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "Required",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionsScreenPreview() {
    TestResQMeshTheme {
        PermissionsScreen(
            onAllSet = {},
            hasPermissions = false,
            requestPermissions = {},
            checkHardware = { false }
        )
    }
}
