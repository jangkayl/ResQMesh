package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.ui.components.layout.ResQCard
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.core.utils.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContainerScreen(
    viewModel: CommunicationViewModel, 
    mediaHelper: MediaHelper,
    onChatSelected: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showNewMessageModal by remember { mutableStateOf(false) }

    if (showNewMessageModal) {
        val uiState by viewModel.uiState.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showNewMessageModal = false },
            containerColor = WarmWhite,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            NewMessageModal(
                uiState = uiState,
                onDismiss = { showNewMessageModal = false },
                onNodeSelected = {
                    showNewMessageModal = false
                    onChatSelected(it)
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Messages",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkGray
                    )
                },
                actions = {
                    IconButton(onClick = { showNewMessageModal = true }) {
                        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = PrimaryRed.copy(alpha = 0.1f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, contentDescription = "New", tint = PrimaryRed)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // High-fidelity Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LightGray.copy(alpha = 0.5f))
                    .padding(4.dp)
            ) {
                ModernTabItem(
                    title = "Private",
                    isSelected = selectedTabIndex == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTabIndex = 0 }
                )
                ModernTabItem(
                    title = "Broadcast",
                    isSelected = selectedTabIndex == 1,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedTabIndex = 1 }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> PrivateChatTab(viewModel, mediaHelper, onChatSelected)
                    1 -> PublicChatTab(viewModel, mediaHelper, onChatSelected)
                }
            }
        }
    }
}

@Composable
fun ModernTabItem(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) WarmWhite else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) PrimaryRed else TextSecondary
        )
    }
}
