package com.example.testresqmesh.feature.setup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.components.layout.ResQGradientOrb
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme

private enum class SetupAccessState {
    RequestAccess,
    EnableHardware,
    Ready
}

@Composable
fun PermissionsScreen(
    onAllSet: () -> Unit,
    hasPermissions: Boolean,
    requestPermissions: () -> Unit,
    checkHardware: () -> Boolean
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isHardwareOn by remember { mutableStateOf(checkHardware()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isHardwareOn = checkHardware()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = when {
        !hasPermissions -> SetupAccessState.RequestAccess
        !isHardwareOn -> SetupAccessState.EnableHardware
        else -> SetupAccessState.Ready
    }

    LaunchedEffect(state) {
        if (state == SetupAccessState.Ready) onAllSet()
    }

    PermissionsContent(
        state = state,
        onPrimaryAction = {
            when (state) {
                SetupAccessState.RequestAccess -> requestPermissions()
                SetupAccessState.EnableHardware -> isHardwareOn = checkHardware()
                SetupAccessState.Ready -> onAllSet()
            }
        }
    )
}

@Composable
private fun PermissionsContent(
    state: SetupAccessState,
    onPrimaryAction: () -> Unit
) {
    val scrollState = rememberScrollState()
    val title = when (state) {
        SetupAccessState.RequestAccess -> stringResource(R.string.permissions_access_title)
        SetupAccessState.EnableHardware -> stringResource(R.string.permissions_hardware_title)
        SetupAccessState.Ready -> stringResource(R.string.permissions_ready_title)
    }
    val description = when (state) {
        SetupAccessState.RequestAccess -> stringResource(R.string.permissions_access_description)
        SetupAccessState.EnableHardware -> stringResource(R.string.permissions_hardware_description)
        SetupAccessState.Ready -> stringResource(R.string.permissions_ready_description)
    }
    val buttonLabel = when (state) {
        SetupAccessState.RequestAccess -> stringResource(R.string.permissions_allow_action)
        SetupAccessState.EnableHardware -> stringResource(R.string.permissions_recheck_action)
        SetupAccessState.Ready -> stringResource(R.string.permissions_continue_action)
    }
    val heroIcon = when (state) {
        SetupAccessState.RequestAccess -> Icons.Outlined.Security
        SetupAccessState.EnableHardware -> Icons.Default.Bluetooth
        SetupAccessState.Ready -> Icons.Default.Check
    }

    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(
                    if (state == SetupAccessState.RequestAccess) R.string.permissions_step_one
                    else R.string.permissions_step_two
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.Medium))
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Spacing.Small))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.Huge))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(144.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ResQGradientOrb(modifier = Modifier.size(96.dp)) {
                            Icon(
                                imageVector = heroIcon,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.Huge))
            PermissionRequirementCard(
                icon = Icons.Default.Bluetooth,
                title = stringResource(R.string.permissions_bluetooth_title),
                description = stringResource(R.string.permissions_bluetooth_description)
            )
            Spacer(Modifier.height(Spacing.Medium))
            PermissionRequirementCard(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.permissions_location_title),
                description = stringResource(R.string.permissions_location_description)
            )

            if (state == SetupAccessState.EnableHardware) {
                Spacer(Modifier.height(Spacing.Medium))
                HardwareWarning()
            }

            Spacer(Modifier.height(Spacing.Medium))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = stringResource(R.string.permissions_extra_access_note),
                    modifier = Modifier.padding(Spacing.Medium),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Spacing.Large))
            ResQButton(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = Spacing.Medium)
            ) {
                Text(buttonLabel, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(Spacing.Small))
            Text(
                text = stringResource(R.string.permissions_offline_note),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionRequirementCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    ResQGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(Spacing.Medium)
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = if (icon == Icons.Default.Bluetooth) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (icon == Icons.Default.Bluetooth) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HardwareWarning() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = com.example.testresqmesh.core.ui.theme.ResQTheme.colors.warningContainer
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = com.example.testresqmesh.core.ui.theme.ResQTheme.colors.warning
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permissions_hardware_warning_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = com.example.testresqmesh.core.ui.theme.ResQTheme.colors.onWarningContainer
                )
                Spacer(Modifier.height(Spacing.ExtraSmall))
                Text(
                    text = stringResource(R.string.permissions_hardware_warning_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = com.example.testresqmesh.core.ui.theme.ResQTheme.colors.onWarningContainer
                )
            }
        }
    }
}

@Preview(name = "Permissions — request access", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PermissionsRequestPreview() {
    TestResQMeshTheme(darkTheme = false) {
        PermissionsContent(SetupAccessState.RequestAccess, onPrimaryAction = {})
    }
}

@Preview(name = "Permissions — turn on hardware", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PermissionsHardwarePreview() {
    TestResQMeshTheme(darkTheme = true) {
        PermissionsContent(SetupAccessState.EnableHardware, onPrimaryAction = {})
    }
}
