package com.example.testresqmesh.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.BuildConfig
import com.example.testresqmesh.core.ui.components.debug.DebugTerminal
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel

@Composable
fun AdvancedScreen(walkieTalkieViewModel: WalkieTalkieViewModel, onBack: () -> Unit) {
    val enabled by walkieTalkieViewModel.isWalkieTalkieMode.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFFBFAFF)).padding(horizontal = Spacing.Large), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Spacer(Modifier.height(10.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }; Text("Advanced", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black) }
            Text("Experimental tools. Use only when needed.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 48.dp)); Spacer(Modifier.height(24.dp))
        }
        item { AdvancedCard(Icons.Default.GraphicEq, "Walkie-talkie", "Play incoming voice messages", trailing = { Switch(checked = enabled, onCheckedChange = { walkieTalkieViewModel.toggleWalkieTalkieMode() }) }) }
        item { Spacer(Modifier.height(14.dp)); AdvancedCard(Icons.Default.Science, "Topology", "View routes from Network") }
    }
    // Intentionally hidden during the shell transition. Keep the debug-only terminal mounted so
    // its implementation can be restored through a future, deliberate entry point.
    if (BuildConfig.DEBUG) DebugTerminal()
}

@Composable private fun AdvancedCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, trailing: @Composable (() -> Unit)? = null) { Surface(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), color = Color.White, shadowElevation = 3.dp) { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Color(0xFF7442C8)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; trailing?.invoke() } } }
