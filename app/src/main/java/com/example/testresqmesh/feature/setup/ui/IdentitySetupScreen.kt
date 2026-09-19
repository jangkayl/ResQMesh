package com.example.testresqmesh.feature.setup.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.inputs.ResQTextField
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modern Secure Digital Passport Identity Setup Screen.
 *
 * Implements a high-trust, civilian-friendly digital wallet pass aesthetic.
 * As the user types their display name and tag, the floating passport card
 * dynamically updates in real-time with responsive animations.
 *
 * Fully keyboard-responsive: with imePadding and smart auto-scrolling, the input boxes
 * and action button glide smoothly into view directly above the soft keyboard.
 */
@Composable
fun IdentitySetupScreen(viewModel: SetupViewModel, onIdentityGenerated: () -> Unit) {
    val context = LocalContext.current
    var customName by remember { mutableStateOf(viewModel.getSavedName(context)) }
    var nodeTag by remember { mutableStateOf(viewModel.getSavedTag(context)) }

    IdentitySetupContent(
        customName = customName,
        onCustomNameChange = { customName = it },
        nodeTag = nodeTag,
        onNodeTagChange = { nodeTag = it },
        onIdentityGenerated = {
            viewModel.checkHardwareAndGoOnline(
                context = context,
                customName = customName.ifEmpty { Build.MODEL },
                nodeTag = nodeTag.ifEmpty { "NODE" },
                teamKey = "PUBLIC"
            )
            onIdentityGenerated()
        }
    )
}

@Composable
fun IdentitySetupContent(
    customName: String,
    onCustomNameChange: (String) -> Unit,
    nodeTag: String,
    onNodeTagChange: (String) -> Unit,
    onIdentityGenerated: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var submitAttempted by remember { mutableStateOf(false) }
    val isNameInvalid = submitAttempted && customName.isBlank()
    val avatarInitial = customName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val primaryColor = MaterialTheme.colorScheme.primary

    // Keyboard detection to dynamically adjust layout and auto-scroll
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val isKeyboardOpen = imeInsets.getBottom(density) > 0

    // Automatically glide input box up above keyboard when soft keyboard appears
    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) {
            delay(150)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Adapt passport card dimensions when keyboard is open so both card and input are visible
    val passportPadding by animateDpAsState(
        targetValue = if (isKeyboardOpen) 14.dp else 20.dp,
        animationSpec = tween(250),
        label = "passportPadding"
    )
    val avatarSize by animateDpAsState(
        targetValue = if (isKeyboardOpen) 50.dp else 68.dp,
        animationSpec = tween(250),
        label = "avatarSize"
    )

    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Large),
            horizontalAlignment = Alignment.Start
        ) {
            // Step indicator pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = primaryColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f))
            ) {
                Text(
                    text = stringResource(R.string.identity_step),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(Spacing.Small))

            Text(
                text = stringResource(R.string.identity_title),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.identity_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(if (isKeyboardOpen) 12.dp else Spacing.Large))

            // ── HERO: The Floating "Secure Digital Passport" Card ──
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = if (isKeyboardOpen) 8.dp else 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = primaryColor.copy(alpha = 0.25f),
                        spotColor = primaryColor.copy(alpha = 0.35f)
                    ),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                border = BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(passportPadding)
                ) {
                    // Top Pass Bar: App identifier & Tag chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ResQTheme.colors.success)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "OFFLINE ID PASS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Dynamic Tag Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = primaryColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = if (nodeTag.isNotBlank()) "#${nodeTag.uppercase()}" else "#NODE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                fontWeight = FontWeight.Bold,
                                color = primaryColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(if (isKeyboardOpen) 10.dp else 16.dp))

                    // Center: Large Dynamic Avatar & Display Name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar Circle
                        Surface(
                            modifier = Modifier.size(avatarSize),
                            shape = CircleShape,
                            color = primaryColor.copy(alpha = 0.20f),
                            border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.5f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = avatarInitial,
                                    style = if (isKeyboardOpen) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (customName.isNotBlank()) customName else "Your Name",
                                style = if (isKeyboardOpen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (customName.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Mesh Participant",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Passport Footer: Security & Encryption Badges
                    if (!isKeyboardOpen) {
                        Spacer(Modifier.height(16.dp))

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "P2P ENCRYPTED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.WifiTethering,
                                    contentDescription = null,
                                    tint = ResQTheme.colors.success,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "READY TO MESH",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = ResQTheme.colors.success
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (isKeyboardOpen) 12.dp else Spacing.ExtraLarge))

            // ── INPUT FORM: Display Name & Node Tag ──
            ResQTextField(
                value = customName,
                onValueChange = onCustomNameChange,
                modifier = Modifier.onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        coroutineScope.launch {
                            delay(150)
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                },
                label = stringResource(R.string.identity_name_label),
                placeholder = stringResource(R.string.identity_name_placeholder),
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                isError = isNameInvalid,
                errorMessage = if (isNameInvalid) stringResource(R.string.identity_name_error) else null
            )

            Spacer(Modifier.height(12.dp))

            ResQTextField(
                value = nodeTag,
                onValueChange = { value ->
                    if (value.length <= 6 && value.all { it.isLetterOrDigit() }) {
                        onNodeTagChange(value.uppercase())
                    }
                },
                modifier = Modifier.onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        coroutineScope.launch {
                            delay(150)
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                },
                label = stringResource(R.string.identity_tag_label),
                placeholder = stringResource(R.string.identity_tag_placeholder)
            )

            Spacer(Modifier.height(Spacing.ExtraSmall))

            Text(
                text = stringResource(R.string.identity_tag_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(if (isKeyboardOpen) 16.dp else Spacing.ExtraLarge))

            // Continue CTA Button
            ResQButton(
                onClick = {
                    submitAttempted = true
                    if (customName.isNotBlank()) onIdentityGenerated()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    text = stringResource(R.string.identity_start_action),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(Spacing.Small))

            Text(
                text = stringResource(R.string.identity_change_note),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "Identity setup", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IdentitySetupPreview() {
    TestResQMeshTheme {
        IdentitySetupContent(
            customName = "Ari Santos",
            onCustomNameChange = {},
            nodeTag = "TEAM1",
            onNodeTagChange = {},
            onIdentityGenerated = {}
        )
    }
}
