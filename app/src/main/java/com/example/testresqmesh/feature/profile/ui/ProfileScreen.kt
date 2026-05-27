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
import com.example.testresqmesh.core.ui.components.layout.ResQCard
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class) 
@Composable
fun ProfileScreen(viewModel: SetupViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety Settings", fontWeight = FontWeight.Bold, color = DarkGray) },
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
                Spacer(modifier = Modifier.height(16.dp))

                // Profile Header Card
                ResQCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 28.dp
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = PrimaryRed.copy(alpha = 0.1f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text(text = uiState.myNodeName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = DarkGray)
                            Text(text = "Active Responder Profile", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("RESCUE NETWORK", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.height(12.dp))
                
                ResQCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                    Column {
                        SettingToggleRow(Icons.Default.Wifi, "Mesh Discovery", "Help others find you offline", true)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = LightGray)
                        SettingToggleRow(Icons.Default.Bluetooth, "Bluetooth Link", "Low-power signal beacon", true)
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), color = LightGray)
                        SettingToggleRow(Icons.Default.BatteryChargingFull, "Eco Mode", "Saves power during long outages", false)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("PRIVACY & DATA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.padding(start = 8.dp))
                Spacer(modifier = Modifier.height(12.dp))

                ResQCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
                    Column(modifier = Modifier.padding(Spacing.Medium)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Secure Encryption", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = DarkGray)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Your messages and location are encrypted end-to-end and never leave the mesh network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { viewModel.goOffline() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryRed.copy(alpha = 0.1f), contentColor = PrimaryRed),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Deactivate Responder Mode", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun SettingToggleRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, checked: Boolean) {
    Row(
        modifier = Modifier.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = DarkGray)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked, 
            onCheckedChange = {},
            colors = SwitchDefaults.colors(checkedThumbColor = WarmWhite, checkedTrackColor = PrimaryRed)
        )
    }
}
