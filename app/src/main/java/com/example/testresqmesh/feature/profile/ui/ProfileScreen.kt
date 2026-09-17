package com.example.testresqmesh.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel

@Composable
fun ProfileScreen(viewModel: SetupViewModel, onAdvanced: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFFBFAFF)).padding(horizontal = Spacing.Large), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(22.dp))
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 4.dp) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(Modifier.size(52.dp), CircleShape, color = Color(0xFF16BCD4)) { Box(contentAlignment = Alignment.Center) { Text(state.myNodeName.firstOrNull()?.uppercase() ?: "R", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge) } }
                    Spacer(Modifier.width(14.dp)); Column { Text(state.myNodeName.ifBlank { "ResQMesh user" }, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Text(if (state.isOnline) "Available nearby" else "Offline", color = if (state.isOnline) Color(0xFF18A66A) else MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Spacer(Modifier.height(28.dp)); Text("APP", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(10.dp))
        }
        item { SettingsCard { SettingRow(Icons.Default.Palette, "Appearance", "Light mode") ; DividerLine(); SettingRow(Icons.Default.Key, "Permissions", "Bluetooth, location, microphone") ; DividerLine(); SettingRow(Icons.Default.Map, "Offline maps", "Download from an SOS map") } }
        item { Spacer(Modifier.height(24.dp)); Text("MORE", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(10.dp)) }
        item { SettingsCard { SettingRow(Icons.Default.Lock, "Privacy", "Private messages need a ready link"); DividerLine(); SettingRow(Icons.Default.Info, "About", "ResQMesh") ; DividerLine(); SettingRow(Icons.Default.Tune, "Advanced", "Experimental tools", onClick = onAdvanced) } }
        item { Spacer(Modifier.height(24.dp)); OutlinedButton(onClick = viewModel::goOffline, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE5484D))) { Icon(Icons.Default.PowerSettingsNew, null); Spacer(Modifier.width(8.dp)); Text("Go offline", fontWeight = FontWeight.Bold) } }
    }
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 3.dp) { Column(content = content) } }
@Composable private fun DividerLine() { HorizontalDivider(color = Color(0xFFF0EDF5), modifier = Modifier.padding(start = 68.dp)) }
@Composable private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: (() -> Unit)? = null) { Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) { Surface(Modifier.size(36.dp), CircleShape, color = Color(0xFFF0ECFA)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color(0xFF7442C8), modifier = Modifier.size(19.dp)) } }; Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
