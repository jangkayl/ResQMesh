package com.example.testresqmesh.feature.incident.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.location.DefaultLocationClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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

    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible
    val titleBringIntoView = remember { BringIntoViewRequester() }
    val descBringIntoView = remember { BringIntoViewRequester() }
    val areaBringIntoView = remember { BringIntoViewRequester() }

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

    BackHandler(onBack = ::handleBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Report Emergency",
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
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (!isImeVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = Spacing.Large, vertical = 8.dp)
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 680.dp)
                    ) {
                        Button(
                            onClick = ::validateAndSubmit,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafetyOrange,
                                contentColor = Color.White
                            )
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Broadcasting to Mesh…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Broadcast Request to Mesh Network", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
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
                    .padding(horizontal = Spacing.Large)
                    .padding(top = Spacing.Small, bottom = Spacing.Small)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                    // 1. Emergency Category (Tactile 2x3 Grid)
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
                                text = "Category",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = "*",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        val chunkedCategories = categories.chunked(3)
                        chunkedCategories.forEach { rowCategories ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowCategories.forEach { cat ->
                                    val isSelected = selectedType == cat
                                    val icon = incidentCategoryIcon(cat)
                                    val cardUrgencyColor = when (cat.lowercase()) {
                                        "medical" -> ResQTheme.colors.sos
                                        "fire" -> ResQTheme.colors.warning
                                        else -> MaterialTheme.colorScheme.primary
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) cardUrgencyColor.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        border = BorderStroke(
                                            if (isSelected) 1.5.dp else 1.dp,
                                            if (isSelected) cardUrgencyColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(72.dp)
                                            .clickable { selectedType = cat }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = cardUrgencyColor,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .align(Alignment.TopEnd)
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) cardUrgencyColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = cat,
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 11.5.sp
                                                    ),
                                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showErrors && selectedType == null) {
                            Text(
                                text = "Please select an emergency category",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // 2. Urgency Level (High-Contrast Segmented Selector)
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
                                text = "Urgency",
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
                            Triple("Moderate", "Standard assistance", MaterialTheme.colorScheme.primary),
                            Triple("Serious", "Urgent / High", ResQTheme.colors.warning),
                            Triple("Critical", "Life threatening", ResQTheme.colors.sos)
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                urgencyOptions.forEach { (level, subtitle, accentColor) ->
                                    val isSelected = selectedUrgency == level
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) accentColor else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 44.dp)
                                            .clickable { selectedUrgency = level }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                                        ) {
                                            Text(
                                                text = level,
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp
                                                ),
                                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
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

                    // 3. Title (required, trimmed, 1-80 chars)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(titleBringIntoView),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isTitleError = showErrors && (title.trim().isEmpty() || title.trim().length > 80)
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title *") },
                            placeholder = { Text("Help moving an injured person") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
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
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        coroutineScope.launch {
                                            delay(200)
                                            titleBringIntoView.bringIntoView()
                                        }
                                    }
                                }
                        )
                    }

                    // 4. Description (optional) + Quick Need Chips
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(descBringIntoView),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            placeholder = { Text("Describe what happened and the assistance needed.") },
                            minLines = 3,
                            maxLines = 5,
                            colors = incidentTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        coroutineScope.launch {
                                            delay(200)
                                            descBringIntoView.bringIntoView()
                                        }
                                    }
                                }
                        )

                        // Quick resource / need tags
                        val quickNeedTags = listOf("Stretcher", "Splint", "First aid kit", "4x4 Transport", "Oxygen", "Water")
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Quick needs (tap to add):",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                quickNeedTags.forEach { tag ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SafetyOrange.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, SafetyOrange.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable {
                                            val addition = "[Need: $tag] "
                                            if (!description.contains(addition)) {
                                                description = if (description.isBlank()) addition else "$description $addition"
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "+ $tag",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = SafetyOrange,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Unified Location & Landmark Card (High-density merged section)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(areaBringIntoView)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = areaDescription,
                                onValueChange = { areaDescription = it },
                                label = { Text("Place or landmark (optional)") },
                                placeholder = { Text("e.g. 2nd Floor, Room 204, North Gate") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = if (attachedLocation != null) ResQTheme.colors.sos else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                colors = incidentTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            coroutineScope.launch {
                                                delay(200)
                                                areaBringIntoView.bringIntoView()
                                            }
                                        }
                                    }
                            )

                            val location = attachedLocation
                            if (location == null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (isAcquiringLocation) "Acquiring GPS snapshot…" else "GPS not attached",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Button(
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
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.defaultMinSize(minHeight = 34.dp)
                                    ) {
                                        if (isAcquiringLocation) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Locating…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        } else {
                                            Icon(Icons.Outlined.LocationOn, null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Attach GPS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                if (locationError != null) {
                                    Text(
                                        text = locationError!!,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = ResQTheme.colors.success,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "GPS Attached",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = ResQTheme.colors.success
                                            )
                                        }
                                        Text(
                                            text = "%.5f, %.5f (±%dm)".format(location.latitude, location.longitude, location.accuracy.toInt()),
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        TextButton(
                                            onClick = ::acquireLocationSnapshot,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                        ) {
                                            Text("Retry", fontSize = 12.sp)
                                        }
                                        TextButton(
                                            onClick = { attachedLocation = null },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.defaultMinSize(minHeight = 32.dp)
                                        ) {
                                            Text("Remove", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
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
