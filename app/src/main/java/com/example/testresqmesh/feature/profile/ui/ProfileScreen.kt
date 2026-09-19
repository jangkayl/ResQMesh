package com.example.testresqmesh.feature.profile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.AppAppearance
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: SetupViewModel,
    appearance: AppAppearance,
    onAppearanceSelected: (AppAppearance) -> Unit,
    onAdvanced: () -> Unit,
    onOfflineMaps: () -> Unit = {},
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showConnectionConfirm by remember { mutableStateOf(false) }

    if (showConnectionConfirm) {
        ModalBottomSheet(
            onDismissRequest = { showConnectionConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (state.isOnline) ResQTheme.colors.sos else ResQTheme.colors.success,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(Spacing.Medium))
                Text(
                    text = if (state.isOnline) "Disconnect from mesh?" else "Connect to mesh?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.Small))
                Text(
                    text = if (state.isOnline) "Going offline will isolate your device from the tactical network. You will stop sending and receiving all data." 
                           else "Going online will connect your device to nearby peers and resume network activity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.Large))
                Button(
                    onClick = {
                        if (state.isOnline) viewModel.goOffline()
                        showConnectionConfirm = false
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isOnline) ResQTheme.colors.sos else ResQTheme.colors.success,
                        contentColor = if (state.isOnline) ResQTheme.colors.onSos else ResQTheme.colors.onSuccess
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (state.isOnline) "Confirm Disconnect" else "Confirm Connect", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(Spacing.Medium))
                OutlinedButton(
                    onClick = { showConnectionConfirm = false },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.Large, vertical = Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(ResQSize.MinimumTouchTarget).offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Your device and display preferences", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            ResQGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(Spacing.Medium),
                shadowElevation = 8.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                state.myNodeName.firstOrNull()?.uppercase() ?: "R",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.Medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.myNodeName.ifBlank { "ResQMesh user" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(
                            if (state.isOnline) "Available nearby" else "Offline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (state.isOnline) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { SettingsLabel("APPEARANCE") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                AppearanceChoice(Icons.Default.Brightness4, "Night Operations", "Default high-contrast field display", appearance == AppAppearance.Night) {
                    onAppearanceSelected(AppAppearance.Night)
                }
                AppearanceChoice(Icons.Default.WbSunny, "Daylight", "High-contrast view for bright conditions", appearance == AppAppearance.Daylight) {
                    onAppearanceSelected(AppAppearance.Daylight)
                }
            }
        }
        item { SettingsLabel("DEVICE") }
        item {
            SettingsCard {
                SettingRow(Icons.Default.Key, "Permissions", "Bluetooth, location, and microphone")
                DividerLine()
                SettingRow(Icons.Default.Map, "Offline maps", "Manage Cebu tactical vector packages", onClick = onOfflineMaps)
            }
        }
        item { SettingsLabel("MORE") }
        item {
            SettingsCard {
                SettingRow(Icons.Default.Lock, "Privacy", "Private messages require a ready link")
                DividerLine()
                SettingRow(Icons.Default.Info, "About", "ResQMesh")
                DividerLine()
                SettingRow(Icons.Default.Tune, "Advanced", "Voice and diagnostic utilities", onClick = onAdvanced)
            }
        }
        item {
            Spacer(Modifier.height(Spacing.Medium))
            OutlinedButton(
                onClick = { showConnectionConfirm = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = if (state.isOnline) MaterialTheme.colorScheme.onSurface else ResQTheme.colors.success)
                Spacer(Modifier.width(Spacing.Small))
                Text(if (state.isOnline) "Go offline" else "Go online", fontWeight = FontWeight.Bold, color = if (state.isOnline) MaterialTheme.colorScheme.onSurface else ResQTheme.colors.success)
            }
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.Small, top = Spacing.Small)
    )
}

@Composable
private fun AppearanceChoice(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(horizontal = Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = if (selected) 0.72f else 1f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(Spacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Text("Active", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            // Removed internal vertical padding, now handled entirely by SettingRow heights
            content = content
        )
    }
}

@Composable
private fun DividerLine() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    val content: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(horizontal = Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(19.dp)) }
            }
            Spacer(Modifier.width(Spacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick == null) {
        Box(modifier = rowModifier, contentAlignment = Alignment.CenterStart, content = { content() })
    } else {
        Surface(modifier = rowModifier, color = Color.Transparent, onClick = onClick, content = { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { content() } })
    }
}
