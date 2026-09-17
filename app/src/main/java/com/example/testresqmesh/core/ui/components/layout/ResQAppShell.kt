package com.example.testresqmesh.core.ui.components.layout

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.SignalWifiStatusbarConnectedNoInternet4
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.buttons.SosHoldButton
import com.example.testresqmesh.core.ui.components.feedback.ResQStateCard
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusChip
import com.example.testresqmesh.core.ui.components.feedback.ResQStatusTone
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import androidx.compose.ui.graphics.Color

enum class ResQDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Home(R.string.nav_home, Icons.Outlined.Home),
    Messages(R.string.nav_messages, Icons.Outlined.ChatBubbleOutline),
    Network(R.string.nav_network, Icons.Outlined.Hub)
}

@Composable
fun ResQAppShell(
    selectedDestination: ResQDestination,
    onDestinationSelected: (ResQDestination) -> Unit,
    onSosActivated: () -> Unit,
    modifier: Modifier = Modifier,
    sosEnabled: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    ResQAuroraBackground(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                ResQBottomBar(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = onDestinationSelected,
                    onSosActivated = onSosActivated,
                    sosEnabled = sosEnabled
                )
            },
            content = content
        )
    }
}

@Composable
private fun ResQBottomBar(
    selectedDestination: ResQDestination,
    onDestinationSelected: (ResQDestination) -> Unit,
    onSosActivated: () -> Unit,
    sosEnabled: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(horizontal = Spacing.Large),
        contentAlignment = Alignment.Center
    ) {
        ResQGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.Medium),
            shape = RoundedCornerShape(40.dp),
            shadowElevation = 18.dp,
            contentAlignment = Alignment.Center
        ) {
            NavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                ResQNavigationItem(
                    destination = ResQDestination.Home,
                    selected = selectedDestination == ResQDestination.Home,
                    onClick = { onDestinationSelected(ResQDestination.Home) }
                )
                ResQNavigationItem(
                    destination = ResQDestination.Messages,
                    selected = selectedDestination == ResQDestination.Messages,
                    onClick = { onDestinationSelected(ResQDestination.Messages) }
                )
                ResQNavigationItem(
                    destination = ResQDestination.Network,
                    selected = selectedDestination == ResQDestination.Network,
                    onClick = { onDestinationSelected(ResQDestination.Network) }
                )
            }
        }
        SosHoldButton(
            onHoldComplete = onSosActivated,
            enabled = sosEnabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = Spacing.Small, end = Spacing.Small)
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ResQNavigationItem(
    destination: ResQDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.labelRes),
                modifier = Modifier.size(24.dp)
            )
        },
        alwaysShowLabel = false,
        label = null,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun ShellPreviewContent(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Good morning",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ResQMesh is ready nearby",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ResQStatusChip(
                label = "Online — Direct",
                tone = ResQStatusTone.Success,
                icon = Icons.Outlined.SignalWifiStatusbarConnectedNoInternet4
            )
        }
        ResQStateCard(
            title = "Ready to communicate",
            message = "Two nearby devices are available for emergency messages.",
            tone = ResQStatusTone.Information
        )
        Spacer(Modifier.height(Spacing.Small))
        Text(
            text = "The SOS control stays available in the main app. Hold it for two seconds to activate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "App shell — light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ResQAppShellLightPreview() {
    TestResQMeshTheme(darkTheme = false) {
        ResQAppShell(
            selectedDestination = ResQDestination.Home,
            onDestinationSelected = {},
            onSosActivated = {},
            content = { ShellPreviewContent(it) }
        )
    }
}

@Preview(name = "App shell — large text", showBackground = true, widthDp = 390, heightDp = 844, fontScale = 1.3f)
@Composable
private fun ResQAppShellLargeTextPreview() {
    TestResQMeshTheme {
        ResQAppShell(
            selectedDestination = ResQDestination.Home,
            onDestinationSelected = {},
            onSosActivated = {},
            content = { ShellPreviewContent(it) }
        )
    }
}
