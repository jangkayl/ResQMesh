package com.example.testresqmesh.feature.comms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SettingsInputAntenna
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.components.GlassSurface
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel

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
            containerColor = Color(0xFF121212), // Very dark
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

@Composable
fun ChatContainerScreenContent(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onNewMessageClick: () -> Unit,
    privateTabContent: @Composable () -> Unit,
    publicTabContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // PEEK HEADER (Always visible when collapsed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COMMUNICATION LINK",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 1.sp,
                fontSize = 14.sp
            )
            IconButton(onClick = onNewMessageClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Outlined.AddBox,
                    contentDescription = "New Message",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Custom Segmented Control
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = Spacing.Medium)
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            intensity = 0.1f
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Private Tab
                SegmentedTabItem(
                    title = "SECURE E2E",
                    icon = Icons.Outlined.Shield,
                    isSelected = selectedTabIndex == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(0) }
                )
                // Broadcast Tab
                SegmentedTabItem(
                    title = "BROADCAST",
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

@Composable
fun SegmentedTabItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)

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
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}
