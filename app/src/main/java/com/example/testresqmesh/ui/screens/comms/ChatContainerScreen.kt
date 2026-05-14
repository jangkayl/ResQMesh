package com.example.testresqmesh.ui.screens.comms

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
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.ui.theme.Spacing
import com.example.testresqmesh.ui.viewmodel.ChatViewModel
import com.example.testresqmesh.utils.MediaHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContainerScreen(viewModel: ChatViewModel, mediaHelper: MediaHelper) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

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
                    IconButton(onClick = { /* TODO: New Message */ }) {
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
                            tint = Color.White
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
                color = Color.Black.copy(alpha = 0.3f)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Private Tab
                    SegmentedTabItem(
                        title = "Private",
                        icon = Icons.Outlined.Shield,
                        isSelected = selectedTabIndex == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTabIndex = 0 }
                    )
                    // Broadcast Tab
                    SegmentedTabItem(
                        title = "Broadcast",
                        icon = Icons.Outlined.SettingsInputAntenna,
                        isSelected = selectedTabIndex == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { selectedTabIndex = 1 }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Small))

            // Content Area
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTabIndex) {
                    0 -> PrivateChatTab(viewModel, mediaHelper)
                    1 -> PublicChatTab(viewModel, mediaHelper)
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
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent
    val contentColor = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)

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
