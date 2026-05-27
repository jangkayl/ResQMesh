package com.example.testresqmesh.feature.profile.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.AppBackground
import com.example.testresqmesh.core.ui.theme.CyanPrimary
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class) 
@Composable
fun ProfileScreen(viewModel: SetupViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Technical Dashboard", fontWeight = FontWeight.Black, color = Color.White) },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.Medium)
        ) {
            item {
                Text("MESH NETWORK PERFORMANCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                
                PerformanceCard()
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("COMMUNICATION HARDWARE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                
                HardwareSettings()
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("DATA & PRIVACY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))
                
                PrivacyCard(onGoOffline = { viewModel.goOffline() })
                
                Spacer(modifier = Modifier.height(24.dp))
                
                IdentityCard(uiState.myNodeName)
                
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun PerformanceCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Store-Carry-Forward", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("ACTIVE RELAY SESSION", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black)
                }
                Surface(color = Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) {
                    Text("Healthy", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("34", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Packets Relayed Today", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
                HorizontalDivider(modifier = Modifier.height(60.dp).width(1.dp).align(Alignment.CenterVertically), color = Color.White.copy(alpha = 0.1f))
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("112", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White)
                    Text("Hops Contributed", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Mock Graph Bars
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val barHeights = listOf(0.4f, 0.6f, 0.3f, 0.8f, 0.5f, 0.2f, 0.9f, 0.7f, 0.4f, 0.6f, 0.8f, 0.5f, 0.3f, 0.7f, 0.4f)
                barHeights.forEach { h ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)).background(CyanPrimary))
                }
            }
        }
    }
}

@Composable
fun HardwareSettings() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            HardwareToggle(Icons.Default.Bluetooth, "Bluetooth LE", "Short-range discovery", true)
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            HardwareToggle(Icons.Default.Wifi, "WiFi Direct", "High-bandwidth relay", false)
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            HardwareToggle(Icons.Default.BatteryChargingFull, "Battery Optimizer", "Reduce ping frequency", true)
        }
    }
}

@Composable
fun HardwareToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, checked: Boolean) {
    Row(
        modifier = Modifier.padding(Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.1f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
        }
        Switch(
            checked = checked, 
            onCheckedChange = {},
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanPrimary)
        )
    }
}

@Composable
fun PrivacyCard(onGoOffline: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Encrypted Cache", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Text("12.4 MB / 100 MB", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))) {
                Box(modifier = Modifier.fillMaxWidth(0.124f).fillMaxHeight().background(CyanPrimary))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Current usage: 12.4%", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                Text("Max limit: 100.0 MB", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Flush Cache", fontSize = 12.sp)
                }
                Button(
                    onClick = onGoOffline,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f), contentColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f))
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Go Offline", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun IdentityCard(name: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(Spacing.Medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("ACTIVE MESH IDENTITY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "RESQM-SHA256-8A2F-9D11-E0B1-4C55-77FF-BC12-001X",
                    modifier = Modifier.padding(Spacing.Medium),
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PROTOCOL V2.1.0-MESH-STABLE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = {}) {
                    Text("Re-generate ID >", color = CyanPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
