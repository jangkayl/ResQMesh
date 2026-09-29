package com.example.testresqmesh.feature.profile.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.testresqmesh.core.ui.components.layout.ResQGlassSurface
import com.example.testresqmesh.core.ui.theme.AppAppearance
import com.example.testresqmesh.core.ui.theme.ResQSize
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.feature.setup.viewmodel.MeshStartError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: SetupViewModel,
    appearance: AppAppearance,
    onAppearanceSelected: (AppAppearance) -> Unit,
    onPermissions: () -> Unit,
    onOfflineMaps: () -> Unit = {},
    onHelp: () -> Unit = {},
    onAbout: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperModeEnabled.collectAsState()
    val isLongRangeProfile by viewModel.isLongRangeProfile.collectAsState()
    val isBackgroundMeshEnabled by viewModel.isBackgroundMeshEnabled.collectAsState()
    var showConnectionConfirm by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<MeshStartError?>(null) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initDeveloperMode(context)
    }

    if (showConnectionConfirm) {
        ModalBottomSheet(
            onDismissRequest = { showConnectionConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (state.isOnline) ResQTheme.colors.sos else ResQTheme.colors.success,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(Spacing.Medium))
                Text(
                    text = if (state.isOnline) "Stop mesh session?" else "Start mesh session?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.Small))
                Text(
                    text = if (state.isOnline) "Stopping the session ends BLE discovery and messaging on this phone."
                           else "Starting the session begins BLE discovery. A usable peer link may take time or remain unavailable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                connectionError?.let { error ->
                    Spacer(Modifier.height(Spacing.Medium))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { liveRegion = LiveRegionMode.Assertive },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(Modifier.padding(Spacing.Medium)) {
                            Text(error.message, color = MaterialTheme.colorScheme.onErrorContainer)
                            TextButton(onClick = {
                                if (error.needsAppPermission) {
                                    showConnectionConfirm = false
                                    onPermissions()
                                } else {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                }
                            }) {
                                Text(if (error.needsAppPermission) "Review app permissions" else "Open phone settings")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.Large))
                Button(
                    onClick = {
                        if (state.isOnline) {
                            viewModel.goOffline()
                            showConnectionConfirm = false
                        } else {
                            connectionError = viewModel.checkHardwareAndGoOnline(
                                context = context,
                                customName = viewModel.getSavedName(context),
                                nodeTag = "",
                                teamKey = "PUBLIC"
                            )
                            if (connectionError == null) showConnectionConfirm = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isOnline) ResQTheme.colors.sos else ResQTheme.colors.success,
                        contentColor = if (state.isOnline) ResQTheme.colors.onSos else ResQTheme.colors.onSuccess
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (state.isOnline) "Stop Session" else "Start Session", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(Spacing.Medium))
                OutlinedButton(
                    onClick = { showConnectionConfirm = false },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text("Privacy & Security Architecture", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrivacyBulletPoint("Offline messaging", "Text and SOS use nearby BLE links and relays without requiring cellular data or Wi-Fi. Map packages are downloaded separately.")
                    PrivacyBulletPoint("Keystore encryption", "An Android Keystore RSA key protects per-message AES-GCM keys. Hardware backing depends on the phone.")
                    PrivacyBulletPoint("Private-message limits", "Private sends require a usable recipient key and route; they do not fall back to public broadcast. First-seen keys are not independently verified.")
                    PrivacyBulletPoint("Offline maps", "An installed map package renders from local storage. Downloading or updating a package requires a connection.")
                }
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.Large, vertical = Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Top Navigation Bar
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(ResQSize.MinimumTouchTarget).offset(x = (-12).dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text("Device configuration and station controls", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Station Identity Card
        item {
            ResQGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(Spacing.Medium),
                shadowElevation = 8.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                state.myNodeName.firstOrNull()?.uppercase() ?: "R",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                    Spacer(Modifier.width(Spacing.Medium))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(state.myNodeName.ifBlank { "ResQMesh Station" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (state.isOnline) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape)
                            )
                            Text(
                                if (state.isOnline) "Mesh session active · Check Network for peers" else "Mesh session offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.isOnline) ResQTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Appearance
        item { SettingsLabel("TACTICAL DISPLAY") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                AppearanceChoice(Icons.Default.Brightness4, "Night Operations", "Pitch-black OLED high-contrast display", appearance == AppAppearance.Night) {
                    onAppearanceSelected(AppAppearance.Night)
                }
                AppearanceChoice(Icons.Default.WbSunny, "Daylight Operations", "High-contrast daylight view for bright outdoor field use", appearance == AppAppearance.Daylight) {
                    onAppearanceSelected(AppAppearance.Daylight)
                }
            }
        }

        // Section 2: Hardware & Radio Control
        item { SettingsLabel("HARDWARE & RADIO") }
        item {
            SettingsCard {
                SettingRow(
                    icon = Icons.Outlined.Key,
                    title = "Permissions",
                    subtitle = "Bluetooth, GPS location, mic, and notifications",
                    onClick = onPermissions
                )
                DividerLine()
                SettingRow(
                    icon = Icons.Outlined.Map,
                    title = "Offline maps",
                    subtitle = "Manage Cebu tactical PMTiles vector packages",
                    onClick = onOfflineMaps
                )
                DividerLine()
                SettingRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Keep mesh active in background",
                    subtitle = if (isBackgroundMeshEnabled) {
                        if (state.isOnline) "Background mode enabled for this session" else "Will start with the next mesh session"
                    } else {
                        "Mesh radio stops when the app closes"
                    },
                    onClick = {
                        viewModel.setBackgroundMeshEnabled(!isBackgroundMeshEnabled)
                    },
                    trailing = {
                        Switch(
                            checked = isBackgroundMeshEnabled,
                            onCheckedChange = viewModel::setBackgroundMeshEnabled
                        )
                    }
                )
                DividerLine()
                SettingRow(
                    icon = Icons.Default.AltRoute,
                    title = "Mesh Routing Profile",
                    subtitle = if (isLongRangeProfile) "Long Range / Trail (TTL 10)" else "Dense Room (TTL 4)",
                    onClick = {
                        viewModel.setMeshProfile(context, !isLongRangeProfile)
                    },
                    trailing = {
                        Switch(
                            checked = isLongRangeProfile,
                            onCheckedChange = { checked ->
                                viewModel.setMeshProfile(context, checked)
                            }
                        )
                    }
                )
            }
        }

        // Section 3: Mission Intel & Support
        item { SettingsLabel("MISSION INTEL & SUPPORT") }
        item {
            SettingsCard {
                SettingRow(
                    icon = Icons.Outlined.Info,
                    title = "How to use ResQMesh",
                    subtitle = "Purpose, local messaging, Radio PTT, and SOS guide",
                    onClick = onHelp
                )
                DividerLine()
                SettingRow(
                    icon = Icons.Outlined.Lock,
                    title = "Privacy & Encryption",
                    subtitle = "BLE messaging, Keystore encryption, and trust limits",
                    onClick = { showPrivacyDialog = true }
                )
                DividerLine()
                SettingRow(
                    icon = Icons.Default.Info,
                    title = "About ResQMesh",
                    subtitle = "Decentralized mesh architecture & technical specs",
                    onClick = onAbout
                )
            }
        }

        // Section 4: Developer Zone (Conditional / PIN gated)
        item { SettingsLabel("DEVELOPER TOOLS") }
        item {
            SettingsCard {
                SettingRow(
                    icon = Icons.Default.Terminal,
                    title = "Developer Debugging Mode",
                    subtitle = if (isDeveloperMode) "Active (PIN 0000 verified)" else "Locked (Requires PIN 0000)",
                    onClick = {
                        if (isDeveloperMode) {
                            viewModel.setDeveloperMode(context, false)
                        } else {
                            pinInput = ""
                            pinError = null
                            showPinDialog = true
                        }
                    },
                    trailing = {
                        Switch(
                            checked = isDeveloperMode,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    pinInput = ""
                                    pinError = null
                                    showPinDialog = true
                                } else {
                                    viewModel.setDeveloperMode(context, false)
                                }
                            }
                        )
                    }
                )
            }
        }

        // Bottom Online/Offline Toggle Button
        item {
            Spacer(Modifier.height(Spacing.Medium))
            OutlinedButton(
                onClick = {
                    connectionError = null
                    showConnectionConfirm = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, if (state.isOnline) ResQTheme.colors.sos.copy(alpha = 0.5f) else ResQTheme.colors.success.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (state.isOnline) ResQTheme.colors.sos else ResQTheme.colors.success
                )
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    text = if (state.isOnline) "Stop mesh session" else "Start mesh session",
                    fontWeight = FontWeight.Bold,
                    color = if (state.isOnline) ResQTheme.colors.sos else ResQTheme.colors.success
                )
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Developer Authentication", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter the 4-digit PIN to enable developer debugging tools and the floating diagnostic overlay.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { input ->
                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                pinInput = input
                                pinError = null
                            }
                        },
                        label = { Text("PIN (0000)") },
                        singleLine = true,
                        isError = pinError != null,
                        supportingText = pinError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = viewModel.setDeveloperMode(context, true, pinInput)
                        if (success) {
                            showPinDialog = false
                        } else {
                            pinError = "Incorrect PIN. Access denied."
                        }
                    }
                ) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PrivacyBulletPoint(title: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = ResQTheme.colors.success,
            modifier = Modifier.size(16.dp).offset(y = 2.dp)
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        ),
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.Small, top = Spacing.Small)
    )
}

@Composable
private fun AppearanceChoice(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(76.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        onClick = onClick
    ) {
        Row(modifier = Modifier.padding(horizontal = Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = if (selected) 0.72f else 1f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.width(Spacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Text("Active", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun DividerLine() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    val content: @Composable () -> Unit = {
        Row(modifier = Modifier.padding(horizontal = Spacing.Medium), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Spacer(Modifier.width(Spacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (onClick == null) {
        Box(modifier = rowModifier, contentAlignment = Alignment.CenterStart, content = { content() })
    } else {
        Surface(modifier = rowModifier, color = Color.Transparent, onClick = onClick, content = { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { content() } })
    }
}
