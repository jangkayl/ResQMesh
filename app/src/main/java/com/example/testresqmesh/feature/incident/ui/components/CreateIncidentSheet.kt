package com.example.testresqmesh.feature.incident.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.location.DefaultLocationClient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateIncidentSheet(
    onDismiss: () -> Unit,
    onSubmit: (title: String, type: String, severity: String, desc: String, area: String, lat: Double?, lng: Double?, time: Long?, acc: Float?) -> Unit,
    submissionError: String? = null,
    isSubmitting: Boolean = false
) {
    val context = LocalContext.current
    val locationClient = remember { DefaultLocationClient(context) }

    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var areaDescription by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUrgency by rememberSaveable { mutableStateOf<String?>(null) }

    var attachedLocation by remember { mutableStateOf<Location?>(null) }
    var isAcquiringLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var showLocationDetails by rememberSaveable { mutableStateOf(false) }

    var showErrors by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val titleFocusRequester = remember { FocusRequester() }
    val typeFocusRequester = remember { FocusRequester() }
    val urgencyFocusRequester = remember { FocusRequester() }

    val categories = listOf("Medical", "Fire", "Search & Rescue", "Infrastructure", "Security", "Other")

    fun acquireLocationSnapshot() {
        isAcquiringLocation = true
        locationError = null
        locationClient.requestPinpointLocation { loc ->
            attachedLocation = loc
            isAcquiringLocation = false
            if (loc == null) {
                locationError = "Could not get a GPS fix. You can still report without location."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            acquireLocationSnapshot()
        } else {
            isAcquiringLocation = false
            locationError = "Location permission denied. You can still report without location."
        }
    }

    val hasDraft = title.isNotBlank() || description.isNotBlank() || areaDescription.isNotBlank() ||
        selectedType != null || selectedUrgency != null || attachedLocation != null

    fun handleBack() {
        if (hasDraft) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    fun validateAndSubmit() {
        val cleanTitle = title.trim()
        val titleValid = cleanTitle.isNotEmpty() && cleanTitle.length <= 80
        val typeValid = selectedType != null
        val urgencyValid = selectedUrgency != null

        if (!titleValid || !typeValid || !urgencyValid) {
            showErrors = true
            when {
                !titleValid -> runCatching { titleFocusRequester.requestFocus() }
                !typeValid -> runCatching { typeFocusRequester.requestFocus() }
                !urgencyValid -> runCatching { urgencyFocusRequester.requestFocus() }
            }
            return
        }

        onSubmit(
            cleanTitle,
            selectedType!!,
            selectedUrgency!!,
            description.trim(),
            areaDescription.trim(),
            attachedLocation?.latitude,
            attachedLocation?.longitude,
            attachedLocation?.time,
            attachedLocation?.takeIf { it.hasAccuracy() }?.accuracy
        )
    }

    Dialog(
        onDismissRequest = ::handleBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Report incident",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = ::handleBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.Large)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 680.dp)
                    ) {
                        Button(
                            onClick = ::validateAndSubmit,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Saving…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("Report incident", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 680.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Spacing.Large, vertical = Spacing.Medium)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Submission error banner if failed
                    if (submissionError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = submissionError,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(Spacing.Medium)
                            )
                        }
                    }

                    // 1. Title (required, trimmed, 1-80 chars)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val isTitleError = showErrors && (title.trim().isEmpty() || title.trim().length > 80)
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title *") },
                            placeholder = { Text("Help moving an injured person") },
                            singleLine = true,
                            isError = isTitleError,
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (isTitleError) {
                                        Text(
                                            text = if (title.trim().isEmpty()) "Title is required" else "Maximum 80 characters",
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    } else {
                                        Text(
                                            text = "Short descriptive title (max 80 chars)",
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text("${title.trim().length}/80")
                                }
                            },
                            colors = incidentTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(titleFocusRequester)
                        )
                    }

                    // 2. Description (optional)
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Describe what happened and the help needed.") },
                        minLines = 3,
                        maxLines = 5,
                        colors = incidentTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 3. Place or landmark (optional)
                    OutlinedTextField(
                        value = areaDescription,
                        onValueChange = { areaDescription = it },
                        label = { Text("Place or landmark") },
                        placeholder = { Text("e.g. North entrance, school building") },
                        singleLine = true,
                        colors = incidentTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 4. Emergency category (visual 1-tap chips)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(typeFocusRequester),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Emergency type",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "*",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = selectedType == cat
                                val icon = incidentCategoryIcon(cat)
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 40.dp)
                                        .clickable { selectedType = cat }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        if (showErrors && selectedType == null) {
                            Text(
                                text = "Please select an emergency type",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // 5. Urgency level (3-column tactical selection cards)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(urgencyFocusRequester),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Urgency level",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "*",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        val urgencyOptions = listOf(
                            Triple("Moderate", "Help needed", MaterialTheme.colorScheme.primary),
                            Triple("Serious", "Urgent", ResQTheme.colors.warning),
                            Triple("Critical", "Life safety", ResQTheme.colors.sos)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            urgencyOptions.forEach { (level, subtitle, accentColor) ->
                                val isSelected = selectedUrgency == level
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        accentColor.copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 58.dp)
                                        .clickable { selectedUrgency = level }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = level,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (showErrors && selectedUrgency == null) {
                            Text(
                                text = "Please select urgency level",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // 6. Attach my location
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Location",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.semantics { heading() }
                        )

                        val location = attachedLocation
                        if (location == null) {
                            OutlinedButton(
                                onClick = {
                                    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (fine || coarse) {
                                        acquireLocationSnapshot()
                                    } else {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }
                                },
                                enabled = !isAcquiringLocation,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                            ) {
                                if (isAcquiringLocation) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Acquiring GPS snapshot…")
                                } else {
                                    Icon(Icons.Outlined.LocationOn, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Attach my location")
                                }
                            }

                            if (locationError != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = locationError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = ::acquireLocationSnapshot) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Retry")
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.LocationOn,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "GPS Attached",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            TextButton(
                                                onClick = ::acquireLocationSnapshot,
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Retry", fontSize = 13.sp)
                                            }
                                            TextButton(
                                                onClick = { attachedLocation = null },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("Remove", fontSize = 13.sp)
                                            }
                                        }
                                    }

                                    // Expandable location details
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showLocationDetails = !showLocationDetails }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Location details",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (showLocationDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    if (showLocationDetails) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Coordinates: ${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (location.hasAccuracy()) {
                                            Text(
                                                text = "Accuracy: ±${location.accuracy.toInt()} m",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "Captured: ${DateUtils.getRelativeTimeSpanString(location.time)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // Unsaved Back confirmation dialog
        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = { Text("Discard draft?") },
                text = { Text("Your entered incident details will be lost.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDiscardDialog = false
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Discard draft")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text("Keep editing")
                    }
                }
            )
        }
    }
}
