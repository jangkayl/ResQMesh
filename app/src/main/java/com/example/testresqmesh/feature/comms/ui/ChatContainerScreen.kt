package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.ui.theme.Spacing
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
    val selectedTabIndex = remember { mutableIntStateOf(0) }
    var showNewMessageModal by remember { mutableStateOf(false) }

    if (showNewMessageModal) {
        val uiState by viewModel.uiState.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showNewMessageModal = false },
            containerColor = MaterialTheme.colorScheme.surface,
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

    ChatContainerScreenContent(
        selectedTabIndex = selectedTabIndex.intValue,
        onTabSelected = { selectedTabIndex.intValue = it },
        onNewMessageClick = { showNewMessageModal = true },
        privateTabContent = { PrivateChatTab(viewModel, mediaHelper, onChatSelected) },
        publicTabContent = { PublicChatTab(viewModel, mediaHelper, onChatSelected) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContainerScreenContent(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onNewMessageClick: () -> Unit,
    privateTabContent: @Composable () -> Unit,
    publicTabContent: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Inbox",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = { AppLogger.toggleTerminal() }) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = "Debug Terminal",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = onNewMessageClick) {
                        Icon(
                            Icons.Outlined.AddBox,
                            contentDescription = "New Message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = { /* TODO: More */ }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Custom Segmented Control
            Surface(
                modifier = Modifier
                    .padding(Spacing.Medium)
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Private Tab
                    SegmentedTabItem(
                        title = "Private",
                        icon = Icons.Outlined.Shield,
                        isSelected = selectedTabIndex == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(0) }
                    )
                    // Broadcast Tab
                    SegmentedTabItem(
                        title = "Broadcast",
                        icon = Icons.Outlined.SettingsInputAntenna,
                        isSelected = selectedTabIndex == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> privateTabContent()
                    1 -> publicTabContent()
                }
            }
        }
    }
}

@Composable
fun SegmentedTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatContainerScreenPreview() {
    TestResQMeshTheme {
        ChatContainerScreenContent(
            selectedTabIndex = 0,
            onTabSelected = {},
            onNewMessageClick = {},
            privateTabContent = {
                Column {
                    // Previews updated to be empty (Production state)
                }
            },
            publicTabContent = {
                Column {
                    // Previews updated to be empty (Production state)
                }
            }
        )
    }
}
