package com.example.testresqmesh.feature.setup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.theme.*
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.testresqmesh.R
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel

@Composable
fun IdentitySetupScreen(viewModel: SetupViewModel, onIdentityGenerated: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    IdentitySetupContent(
        connectionStatus = uiState.connectionStatus,
        onIdentityGenerated = {
            viewModel.checkHardwareAndGoOnline(context, Build.MODEL, "NODE", "PUBLIC")
            onIdentityGenerated()
        }
    )
}

@Composable
fun IdentitySetupContent(connectionStatus: String, onIdentityGenerated: () -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Step 2: Identity Setup",
            style = MaterialTheme.typography.labelMedium,
            color = CyanPrimary,
            modifier = Modifier
                .align(Alignment.Start)
                .clip(RoundedCornerShape(12.dp))
                .background(CyanPrimary.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No Signal?\nNo Problem.",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.align(Alignment.Start),
            lineHeight = 44.sp
        )

        Spacer(modifier = Modifier.height(Spacing.Medium))

        Text(
            text = "ResQMesh creates a secure, private identity that works entirely without cellular or internet connectivity.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Large Logo Surface
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.ic_resqmesh_logo),
                    contentDescription = "ResQMesh Logo",
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.Medium))

        Surface(
            color = if (connectionStatus.contains("ERROR")) Color.Red.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
        ) {
            Text(
                if (connectionStatus.contains("ERROR")) "SYSTEM ERROR" else "READY TO SYNC",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Secure Terminal Output
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            color = Color(0xFF1E293B), // Darker slate
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(Spacing.Medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(">_", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SECURE TERMINAL OUTPUT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.3f))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.White.copy(alpha = 0.1f))
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (connectionStatus.contains("ERROR")) Color.Red.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TechnicalInfoCard(
                icon = "⚙️",
                title = "On-Device Encryption",
                modifier = Modifier.weight(1f)
            )
            TechnicalInfoCard(
                icon = "🔗",
                title = "Zero-Cloud Dependency",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        ResQButton(
            onClick = onIdentityGenerated,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Generate Secure ID", fontWeight = FontWeight.Black)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "RESQMESH UTILIZES 256-BIT ENCRYPTION.\nYOUR KEYS NEVER LEAVE THIS DEVICE.",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 0.5.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun IdentitySetupScreenPreview() {
    TestResQMeshTheme {
        IdentitySetupContent(
            connectionStatus = "READY TO SYNC",
            onIdentityGenerated = {}
        )
    }
}

@Composable
fun TechnicalInfoCard(icon: String, title: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 12.sp
            )
        }
    }
}
