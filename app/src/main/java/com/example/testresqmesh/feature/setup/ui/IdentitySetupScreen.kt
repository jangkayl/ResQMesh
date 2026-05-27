package com.example.testresqmesh.feature.setup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.inputs.ResQTextField
import com.example.testresqmesh.core.ui.components.layout.ResQCard
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import android.os.Build

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
    var name by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var bloodType by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = "Welcome to ResQMesh",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = DarkGray,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your safe link when networks fail.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Main Profile Card
        ResQCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 32.dp
        ) {
            Column(
                modifier = Modifier.padding(Spacing.Medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = PrimaryRed.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(40.dp))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Emergency Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkGray
                )

                Text(
                    text = "Optional details to help responders.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(32.dp))

                ResQTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Your Name",
                    placeholder = "e.g. John Doe"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ResQTextField(
                        value = bloodType,
                        onValueChange = { bloodType = it },
                        label = "Blood Type",
                        placeholder = "O+",
                        modifier = Modifier.weight(1f)
                    )
                    ResQTextField(
                        value = emergencyContact,
                        onValueChange = { emergencyContact = it },
                        label = "Contact #",
                        placeholder = "Emergency phone",
                        modifier = Modifier.weight(2f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Reassurance Card
        ResQCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = PrimaryRed.copy(alpha = 0.05f),
            elevation = 0.dp,
            cornerRadius = 24.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryRed, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "No internet required. No account needed. Privacy is built-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(48.dp))

        ResQButton(
            onClick = onIdentityGenerated,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("Start Using ResQMesh", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "By continuing, you enable decentralized mesh networking.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}
