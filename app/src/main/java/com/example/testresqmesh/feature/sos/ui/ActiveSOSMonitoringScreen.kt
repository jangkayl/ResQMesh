package com.example.testresqmesh.feature.sos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

@Composable
fun ActiveSOSMonitoringScreen(
    commsViewModel: CommunicationViewModel,
    onResolve: () -> Unit
) {
    val activeSosId by commsViewModel.activeSosMessageId.collectAsState()
    val chatState by commsViewModel.uiState.collectAsState()
    
    val activeMessage = chatState.publicMessages.find { it.id == activeSosId }
    val deliveredNodes = activeMessage?.deliveredTo ?: emptyList()
    
    val darkBg = Color(0xFF1A1A1A)
    val sosRed = Color(0xFFFF5252)
    val successGreen = Color(0xFF4CAF50)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(Spacing.Large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Surface(
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = sosRed.copy(alpha = 0.2f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = sosRed) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "SOS ACTIVE",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = sosRed
        )
        
        Text(
            text = "Broadcasting emergency to the mesh network. Do not close the app.",
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = Color.Black.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = "RECEIVED BY (${deliveredNodes.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                if (deliveredNodes.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Awaiting receipts...",
                            color = Color.White.copy(alpha = 0.3f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(deliveredNodes.toList()) { nodeName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = successGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = nodeName, color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onResolve,
            colors = ButtonDefaults.buttonColors(containerColor = successGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp)
        ) {
            Text(
                "RESOLVE SOS & STOP FLOOD",
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}
