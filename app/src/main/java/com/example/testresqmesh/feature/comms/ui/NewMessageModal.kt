package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.components.layout.ResQCard
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.ui.state.ChatUiState
import com.example.testresqmesh.core.model.KnownNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageModal(
    uiState: ChatUiState,
    onNodeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val knownNodes = uiState.knownNodes.sortedByDescending { it.isDirect }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(WarmWhite)
            .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "New Private Chat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = DarkGray
            )
            
            IconButton(
                onClick = { /* Refresh handled by VM */ },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryRed.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = PrimaryRed)
            }
        }

        Text(
            "Select a nearby responder to start a mesh-secured session.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        if (knownNodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PrimaryRed, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Searching for nearby responders...",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(knownNodes) { node ->
                    NodeSelectionItem(
                        node = node,
                        onClick = { 
                            onNodeSelected(node.name)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NodeSelectionItem(node: KnownNode, onClick: () -> Unit) {
    ResQCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        cornerRadius = 20.dp,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = PrimaryRed.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        node.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryRed
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkGray
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = if (node.isDirect) SuccessGreen else WarningAmber, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (node.isDirect) "Direct Link" else "Mesh Hop",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = LightGray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
