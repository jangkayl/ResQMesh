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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
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
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import androidx.compose.ui.graphics.Color

enum class ResQDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Mission(R.string.nav_mission, Icons.Outlined.MyLocation),
    Messages(R.string.nav_messages, Icons.Outlined.ChatBubbleOutline),
    Voice(R.string.nav_voice, Icons.Outlined.GraphicEq)
}

@Composable
fun ResQAppShell(
    selectedDestination: ResQDestination,
    onDestinationSelected: (ResQDestination) -> Unit,
    onSosActivated: () -> Unit,
    modifier: Modifier = Modifier,
    sosEnabled: Boolean = true,
    showNavigation: Boolean = true,
    content: @Composable (PaddingValues) -> Unit
) {
    ResQAuroraBackground(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (showNavigation) {
                    ResQBottomBar(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = onDestinationSelected,
                        onSosActivated = onSosActivated,
                        sosEnabled = sosEnabled
                    )
                }
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
            .height(92.dp)
            .padding(horizontal = Spacing.Large),
        contentAlignment = Alignment.Center
    ) {
        ResQGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.Center),
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
                    destination = ResQDestination.Mission,
                    selected = selectedDestination == ResQDestination.Mission,
                    onClick = { onDestinationSelected(ResQDestination.Mission) }
                )
                ResQNavigationItem(
                    destination = ResQDestination.Messages,
                    selected = selectedDestination == ResQDestination.Messages,
                    onClick = { onDestinationSelected(ResQDestination.Messages) }
                )
                ResQNavigationItem(
                    destination = ResQDestination.Voice,
                    selected = selectedDestination == ResQDestination.Voice,
                    onClick = { onDestinationSelected(ResQDestination.Voice) }
                )
                SosNavigationItem(onClick = onSosActivated, enabled = sosEnabled)
            }
        }
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
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        icon = {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.labelRes),
                modifier = Modifier.size(28.dp) // Slightly larger since there's no text
            )
        },
        alwaysShowLabel = false
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SosNavigationItem(
    onClick: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .width(68.dp)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (enabled) ResQTheme.colors.sos else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) ResQTheme.colors.onSos else MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClick,
            enabled = enabled
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = stringResource(R.string.sos), modifier = Modifier.size(19.dp))
                Text("SOS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            }
        }
    }
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
                    text = "Mission control",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your local mesh at a glance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "The SOS action opens a dedicated type-and-slide flow.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(name = "App shell — night operations", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ResQAppShellLightPreview() {
    TestResQMeshTheme(darkTheme = false) {
        ResQAppShell(
            selectedDestination = ResQDestination.Mission,
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
            selectedDestination = ResQDestination.Mission,
            onDestinationSelected = {},
            onSosActivated = {},
            content = { ShellPreviewContent(it) }
        )
    }
}
