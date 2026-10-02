package com.example.testresqmesh.core.ui.components.layout

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme

enum class ResQDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    Mission(R.string.nav_mission, Icons.Outlined.Home),
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
    navigationReminder: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            bottomBar = {
                if (showNavigation) {
                    Column {
                    navigationReminder?.let { reminder ->
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            reminder()
                        }
                    }
                    ResQFloatingIslandNavBar(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = onDestinationSelected,
                        onSosActivated = onSosActivated,
                        sosEnabled = sosEnabled
                    )
                    }
                }
            },
            content = { scaffoldPadding ->
                content(
                    PaddingValues(
                        top = scaffoldPadding.calculateTopPadding(),
                        bottom = 0.dp
                    )
                )
            }
        )
    }
}

@Composable
private fun ResQFloatingIslandNavBar(
    selectedDestination: ResQDestination,
    onDestinationSelected: (ResQDestination) -> Unit,
    onSosActivated: () -> Unit,
    sosEnabled: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating Translucent Glass Island
        ResQGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            shape = RoundedCornerShape(34.dp),
            shadowElevation = 20.dp,
            contentAlignment = Alignment.Center,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tactical Navigation Tabs
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResQIslandNavItem(
                        destination = ResQDestination.Mission,
                        selected = selectedDestination == ResQDestination.Mission,
                        onClick = { onDestinationSelected(ResQDestination.Mission) }
                    )
                    ResQIslandNavItem(
                        destination = ResQDestination.Messages,
                        selected = selectedDestination == ResQDestination.Messages,
                        onClick = { onDestinationSelected(ResQDestination.Messages) }
                    )
                    ResQIslandNavItem(
                        destination = ResQDestination.Voice,
                        selected = selectedDestination == ResQDestination.Voice,
                        onClick = { onDestinationSelected(ResQDestination.Voice) }
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                )

                Spacer(Modifier.width(8.dp))

                ResQStaticSosButton(
                    onClick = onSosActivated,
                    enabled = sosEnabled
                )
            }
        }
    }
}

@Composable
private fun ResQIslandNavItem(
    destination: ResQDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animatedIconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        label = "iconColor"
    )
    val animatedBgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
        label = "bgColor"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = animatedBgColor,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)) else null
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.labelRes),
                modifier = Modifier.size(24.dp),
                tint = animatedIconColor
            )

            Spacer(Modifier.height(3.dp))

            // Neon glowing active indicator dot
            Box(
                modifier = Modifier
                    .size(if (selected) 4.dp else 0.dp)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            )
        }
    }
}

@Composable
private fun ResQStaticSosButton(
    onClick: () -> Unit,
    enabled: Boolean
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(width = 62.dp, height = 50.dp)
                .shadow(
                    elevation = if (enabled) 12.dp else 0.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = ResQTheme.colors.sos,
                    spotColor = ResQTheme.colors.sos
                ),
            shape = RoundedCornerShape(20.dp),
            color = if (enabled) ResQTheme.colors.sos else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (enabled) ResQTheme.colors.onSos else MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (enabled) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)) else null,
            onClick = onClick,
            enabled = enabled
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = stringResource(R.string.sos),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "SOS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    ),
                    fontWeight = FontWeight.Black
                )
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
