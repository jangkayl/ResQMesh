package com.example.testresqmesh.feature.setup.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme

private enum class SetupAccessState {
    RequestAccess,
    EnableHardware,
    Ready
}

/**
 * Modern High-Trust Permissions & Hardware Access Screen.
 *
 * Implements a clear, reassuring onboarding card flow explaining why Bluetooth and Location
 * are required for offline peer-to-peer mesh operations.
 *
 * Checks device permissions reactively on resume and provides an explicit 'Proceed to Next Step'
 * button once all access is confirmed.
 */
@Composable
fun PermissionsScreen(
    onAllSet: () -> Unit,
    hasPermissions: Boolean,
    requestPermissions: () -> Unit,
    checkHardware: () -> Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Helper to evaluate actual Android runtime permissions
    fun checkDevicePermissions(): Boolean {
        if (hasPermissions) return true
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasLocation = fineLocation || coarseLocation

        val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
        return hasLocation && hasBluetooth
    }

    var isHardwareOn by remember { mutableStateOf(checkHardware()) }
    var permissionsGranted by remember(hasPermissions) { mutableStateOf(hasPermissions || checkDevicePermissions()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isHardwareOn = checkHardware()
                permissionsGranted = hasPermissions || checkDevicePermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val state = when {
        !permissionsGranted -> SetupAccessState.RequestAccess
        !isHardwareOn -> SetupAccessState.EnableHardware
        else -> SetupAccessState.Ready
    }

    PermissionsContent(
        state = state,
        onPrimaryAction = {
            when (state) {
                SetupAccessState.RequestAccess -> {
                    // Check if already granted in OS before requesting
                    if (checkDevicePermissions()) {
                        permissionsGranted = true
                        isHardwareOn = checkHardware()
                    } else {
                        requestPermissions()
                    }
                }
                SetupAccessState.EnableHardware -> {
                    isHardwareOn = checkHardware()
                    permissionsGranted = hasPermissions || checkDevicePermissions()
                }
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
    val isReady = state == SetupAccessState.Ready

    val title = when (state) {
        SetupAccessState.RequestAccess -> stringResource(R.string.permissions_access_title)
        SetupAccessState.EnableHardware -> stringResource(R.string.permissions_hardware_title)
        SetupAccessState.Ready -> stringResource(R.string.permissions_ready_title)
    }
    val description = when (state) {
        SetupAccessState.RequestAccess -> stringResource(R.string.onboarding_permissions_access_description)
        SetupAccessState.EnableHardware -> stringResource(R.string.onboarding_permissions_hardware_description)
        SetupAccessState.Ready -> stringResource(R.string.onboarding_permissions_ready_description)
    }
    val buttonLabel = when (state) {
        SetupAccessState.RequestAccess -> stringResource(R.string.onboarding_permissions_allow_action)
        SetupAccessState.EnableHardware -> stringResource(R.string.permissions_recheck_action)
        SetupAccessState.Ready -> "Proceed to Next Step"
    }
    val heroIcon = when (state) {
        SetupAccessState.RequestAccess -> Icons.Outlined.Security
        SetupAccessState.EnableHardware -> Icons.Default.Bluetooth
        SetupAccessState.Ready -> Icons.Default.Check
    }
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = if (isReady) ResQTheme.colors.success else primaryColor

    // Subtle pulsing hero aura
    val infiniteTransition = rememberInfiniteTransition(label = "heroPulse")
    val heroPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroPulseAlpha"
    )

    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge),
            horizontalAlignment = Alignment.Start
        ) {
            // Step indicator pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
            ) {
                Text(
                    text = if (isReady) "ALL ACCESS GRANTED" else stringResource(
                        if (state == SetupAccessState.RequestAccess) R.string.permissions_step_one
                        else R.string.permissions_step_two
                    ),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(Spacing.Small))

            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.Large))

            // Hero Glowing Security Hub
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .size(112.dp)
                        .shadow(
                            elevation = 14.dp,
                            shape = CircleShape,
                            ambientColor = accentColor.copy(alpha = 0.3f),
                            spotColor = accentColor.copy(alpha = 0.5f)
                        ),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    border = BorderStroke(2.dp, accentColor.copy(alpha = heroPulseAlpha))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = heroIcon,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            tint = accentColor
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.Large))

            // Requirement 1: Nearby Devices / Bluetooth
            PermissionRequirementCard(
                icon = Icons.Default.Bluetooth,
                title = stringResource(R.string.permissions_bluetooth_title),
                description = stringResource(R.string.onboarding_permissions_bluetooth_description),
                accentColor = primaryColor,
                isGranted = isReady
            )

            Spacer(Modifier.height(12.dp))

            // Requirement 2: Location (Radio Scanning)
            PermissionRequirementCard(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.permissions_location_title),
                description = stringResource(R.string.onboarding_permissions_location_description),
                accentColor = ResQTheme.colors.success,
                isGranted = isReady
            )

            if (state == SetupAccessState.EnableHardware) {
                Spacer(Modifier.height(12.dp))
                HardwareWarning()
            }

            Spacer(Modifier.height(Spacing.Medium))

            // Extra offline access reassurance note
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Text(
                    text = stringResource(R.string.onboarding_permissions_extra_access_note),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(Spacing.Large))

            // Action CTA Button
            ResQButton(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(Spacing.Small))

            Text(
                text = stringResource(R.string.onboarding_permissions_offline_note),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PermissionRequirementCard(
    icon: ImageVector,
    title: String,
    description: String,
    accentColor: androidx.compose.ui.graphics.Color,
    isGranted: Boolean = false
) {
    ResQGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isGranted) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ResQTheme.colors.success.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, ResQTheme.colors.success.copy(alpha = 0.35f)),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = ResQTheme.colors.success,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Ready",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ResQTheme.colors.success
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareWarning() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ResQTheme.colors.warningContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, ResQTheme.colors.warning.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = ResQTheme.colors.warning,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permissions_hardware_warning_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ResQTheme.colors.onWarningContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.onboarding_permissions_hardware_warning_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = ResQTheme.colors.onWarningContainer
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
