package com.example.testresqmesh.feature.sos.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Location
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.testresqmesh.core.location.LocationClient
import com.example.testresqmesh.core.map.model.MapPackageManifest
import com.example.testresqmesh.core.map.model.MapPackageStatus
import com.example.testresqmesh.core.map.storage.MapStorageGuard
import com.example.testresqmesh.core.map.storage.MapStyleValidator
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.SignalRed
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.location.DefaultLocationClient
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tactical Coordinate display formats.
 */
enum class CoordinateFormatTab {
    DECIMAL,
    MGRS,
    W3W
}

/**
 * Presentation state for [SosMapScreen].
 */
sealed class SosMapViewState {
    /** Alert does not contain GPS coordinates. */
    data object NoLocation : SosMapViewState()

    /**
     * Alert contains valid coordinates, but a verified offline vector map package
     * is not active. Fallback displays high-vis decimal coordinates, sender, and status.
     */
    data class MaplessFallback(
        val lat: Double,
        val lng: Double,
        val formattedLat: String,
        val formattedLng: String,
        val statusMessage: String,
        val isFallbackActive: Boolean = true
    ) : SosMapViewState()

    /**
     * Alert contains valid coordinates and a verified offline vector map package
     * is active. MapLibre Native renders the local PMTiles package via local style.
     */
    data class MapReady(
        val lat: Double,
        val lng: Double,
        val formattedLat: String,
        val formattedLng: String,
        val styleFile: File,
        val manifest: MapPackageManifest,
        val attribution: String
    ) : SosMapViewState()
}

/**
 * Resolves the deterministic [SosMapViewState] given an alert message and storage package status.
 */
fun resolveSosMapViewState(
    alertMessage: ChatMessage,
    packageStatus: MapPackageStatus
): SosMapViewState {
    val lat = alertMessage.locationLat
    val lng = alertMessage.locationLng
    if (lat == null || lng == null) {
        return SosMapViewState.NoLocation
    }

    return if (packageStatus is MapPackageStatus.Installed) {
        SosMapViewState.MapReady(
            lat = lat,
            lng = lng,
            formattedLat = formatCoordinate(lat, isLat = true),
            formattedLng = formatCoordinate(lng, isLat = false),
            styleFile = packageStatus.styleFile,
            manifest = packageStatus.manifest,
            attribution = packageStatus.manifest.attribution.ifBlank { "© OpenStreetMap contributors" }
        )
    } else {
        val statusMsg = when (packageStatus) {
            is MapPackageStatus.Downloading -> "Map download in progress"
            is MapPackageStatus.Verifying -> "Map verification in progress"
            is MapPackageStatus.Extracting -> "Map extraction in progress"
            is MapPackageStatus.Error -> "Cebu offline map not installed"
            is MapPackageStatus.NotInstalled -> "Cebu offline map not installed"
            is MapPackageStatus.Installed -> "Offline map installed"
        }
        SosMapViewState.MaplessFallback(
            lat = lat,
            lng = lng,
            formattedLat = formatCoordinate(lat, isLat = true),
            formattedLng = formatCoordinate(lng, isLat = false),
            statusMessage = statusMsg
        )
    }
}

@Composable
fun SosMapScreen(
    alertMessage: ChatMessage,
    onBack: () -> Unit,
    storageGuard: MapStorageGuard? = null
) {
    val context = LocalContext.current
    val guard = remember(storageGuard) {
        storageGuard
            ?: runCatching { org.koin.java.KoinJavaComponent.getKoin().getOrNull<MapStorageGuard>() }.getOrNull()
            ?: MapStorageGuard(context)
    }

    val packageStatus = remember(guard) { guard.getActivePackageStatus() }
    val viewState = remember(alertMessage, packageStatus) {
        resolveSosMapViewState(alertMessage, packageStatus)
    }

    when (viewState) {
        is SosMapViewState.NoLocation -> {
            ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    TacticalHeaderBar(
                        senderName = alertMessage.senderName,
                        onBack = onBack
                    )
                    NoLocationFallback(alertMessage = alertMessage)
                }
            }
        }
        is SosMapViewState.MaplessFallback -> {
            ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    TacticalHeaderBar(
                        senderName = alertMessage.senderName,
                        onBack = onBack
                    )
                    MaplessSosFallback(
                        alertMessage = alertMessage,
                        state = viewState
                    )
                }
            }
        }
        is SosMapViewState.MapReady -> {
            // Full edge-to-edge Tactical Map Container with user location, rotation & collapsible sheet
            MapLibreLocalMapContainer(
                alertMessage = alertMessage,
                state = viewState,
                onBack = onBack
            )
        }
    }
}

@Composable
private fun TacticalHeaderBar(
    senderName: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.width(Spacing.Small))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing Red SOS Indicator Dot
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(SignalRed.copy(alpha = alpha), CircleShape)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "SOS TACTICAL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = SignalRed
                )
            }
            Text(
                text = "BEACON: $senderName",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MapLibreLocalMapContainer(
    alertMessage: ChatMessage,
    state: SosMapViewState.MapReady,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val locationClient = remember {
        runCatching { org.koin.java.KoinJavaComponent.getKoin().getOrNull<LocationClient>() }.getOrNull()
            ?: DefaultLocationClient(context)
    }

    var myLocation by remember { mutableStateOf<Location?>(null) }
    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapBearing by remember { mutableFloatStateOf(0f) }
    var activeTab by remember { mutableStateOf(CoordinateFormatTab.DECIMAL) }
    var isSheetExpanded by remember { mutableStateOf(true) }

    // User location markers/vector line references
    var userMarker by remember { mutableStateOf<Marker?>(null) }
    var rangePolyline by remember { mutableStateOf<Polyline?>(null) }

    // Fetch user location
    LaunchedEffect(locationClient) {
        val cached = locationClient.getLastKnownLocation()
        if (cached != null) {
            myLocation = cached
        }
        locationClient.requestPinpointLocation { pinpoint ->
            if (pinpoint != null) {
                myLocation = pinpoint
            }
        }
    }

    // Dynamic distance & bearing calculation between User and SOS Beacon
    val telemetry = remember(myLocation, state.lat, state.lng) {
        val userLoc = myLocation
        if (userLoc != null) {
            val results = FloatArray(2)
            Location.distanceBetween(userLoc.latitude, userLoc.longitude, state.lat, state.lng, results)
            val dist = results[0]
            val bearing = (results[1] + 360f) % 360f
            val distStr = if (dist < 1000f) "${dist.roundToInt()} m" else String.format(Locale.US, "%.1f km", dist / 1000f)
            val bearingStr = "BEARING %03d° %s".format(bearing.roundToInt(), bearingToCardinal(bearing.toDouble()))
            Pair(distStr, bearingStr)
        } else {
            Pair("LOCATING...", "ACQUIRING GPS")
        }
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context)
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // Function to smart-fit camera to show both user and SOS target
    fun frameTacticalCorridor(map: MapLibreMap) {
        val userLoc = myLocation
        if (userLoc != null) {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(userLoc.latitude, userLoc.longitude))
                .include(LatLng(state.lat, state.lng))
                .build()
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 180))
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(state.lat, state.lng), 15.0))
        }
    }

    // Update map annotations when user location updates
    LaunchedEffect(myLocation, mapLibreInstance) {
        val map = mapLibreInstance ?: return@LaunchedEffect
        val userLoc = myLocation ?: return@LaunchedEffect

        // Update or add user marker
        userMarker?.let { map.removeMarker(it) }
        rangePolyline?.let { map.removePolyline(it) }

        val iconFactory = IconFactory.getInstance(context)
        val myLocationIcon = iconFactory.fromBitmap(createMyLocationMarkerBitmap(context))

        userMarker = map.addMarker(
            MarkerOptions()
                .position(LatLng(userLoc.latitude, userLoc.longitude))
                .title("YOU")
                .snippet("My Current Location")
                .icon(myLocationIcon)
        )

        // Draw tactical connecting range vector line in high-vis Safety Orange
        rangePolyline = map.addPolyline(
            PolylineOptions()
                .add(LatLng(userLoc.latitude, userLoc.longitude))
                .add(LatLng(state.lat, state.lng))
                .color(android.graphics.Color.parseColor("#FF9100"))
                .width(3.5f)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full Edge-to-edge Map Surface
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { mapLibreMap ->
                        mapLibreInstance = mapLibreMap

                        // Enable free two-finger rotation gestures & track bearing
                        mapLibreMap.uiSettings.isRotateGesturesEnabled = true
                        mapLibreMap.uiSettings.isCompassEnabled = false // using custom HUD compass
                        mapLibreMap.uiSettings.isLogoEnabled = false
                        mapLibreMap.uiSettings.isAttributionEnabled = false

                        mapLibreMap.addOnCameraMoveListener {
                            mapBearing = mapLibreMap.cameraPosition.bearing.toFloat()
                        }

                        val resolvedStyle = MapStyleValidator.resolveStyleForRuntime(
                            packageDir = state.styleFile.parentFile ?: state.styleFile,
                            styleFile = state.styleFile
                        )
                        val styleBuilder = if (resolvedStyle.isNotBlank()) {
                            Style.Builder().fromJson(resolvedStyle)
                        } else {
                            Style.Builder().fromUri("file://${state.styleFile.absolutePath}")
                        }

                        mapLibreMap.setStyle(styleBuilder) {
                            // SOS Beacon Target Marker with distinct Signal Red custom icon
                            val sosIcon = IconFactory.getInstance(context).fromBitmap(createSosMarkerBitmap(context, "SOS"))
                            mapLibreMap.addMarker(
                                MarkerOptions()
                                    .position(LatLng(state.lat, state.lng))
                                    .title("SOS: ${alertMessage.senderName}")
                                    .snippet(alertMessage.text.ifBlank { "Emergency beacon" })
                                    .icon(sosIcon)
                            )

                            // Initial camera framing
                            frameTacticalCorridor(mapLibreMap)
                        }
                    }
                }
            }
        )

        // Top Tactical Glass Bar (Cleaned up, no NVG/AMB filters)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.width(Spacing.Small))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(SignalRed.copy(alpha = alpha), CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "SOS TACTICAL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = SignalRed
                        )
                    }
                    Text(
                        text = "BEACON: ${alertMessage.senderName.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Air-Gapped Offline Verified Pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ResQTheme.colors.success.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(ResQTheme.colors.success, CircleShape)
                        )
                        Text(
                            text = "OFFLINE v${state.manifest.version}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ResQTheme.colors.success
                        )
                    }
                }
            }

            // Sub-bar with Compass Rose
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium),
                horizontalArrangement = Arrangement.End
            ) {
                // Interactive Compass Dial (Tracks True North, click to animate back to North)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .clickable {
                            mapLibreInstance?.animateCamera(CameraUpdateFactory.bearingTo(0.0))
                            Toast.makeText(context, "Aligned to True North", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = -mapBearing
                            }
                        ) {
                            Text(
                                text = "▲",
                                fontSize = 11.sp,
                                color = SignalRed,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "N",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.offset(y = (-2).dp)
                            )
                        }
                    }
                }
            }
        }

        val fabBottomPadding by animateDpAsState(
            targetValue = if (isSheetExpanded) 250.dp else 90.dp,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "fabBottomPadding"
        )

        // Floating Target Re-Center Action Button (Frames tactical corridor between you & beacon)
        FloatingActionButton(
            onClick = {
                val map = mapLibreInstance ?: return@FloatingActionButton
                frameTacticalCorridor(map)
                Toast.makeText(context, "Tactical corridor framed", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = Spacing.Medium, bottom = fabBottomPadding)
                .size(42.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = SignalRed,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "Fit Tactical Corridor",
                modifier = Modifier.size(24.dp)
            )
        }

        // Draggable & Collapsible Bottom Sheet
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (delta > 20) {
                            isSheetExpanded = false // Drag down collapses
                        } else if (delta < -20) {
                            isSheetExpanded = true // Drag up expands
                        }
                    },
                    orientation = Orientation.Vertical
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Drag Handle / Tap to Toggle Collapse
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSheetExpanded = !isSheetExpanded }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(5.dp)
                            .background(
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                                RoundedCornerShape(3.dp)
                            )
                    )
                }

                // Collapsed Compact 56dp Bar Preview (Always visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSheetExpanded = !isSheetExpanded }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = SignalRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = alertMessage.senderName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = telemetry.second,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = telemetry.first,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = SignalRed
                        )
                        IconButton(
                            onClick = { isSheetExpanded = !isSheetExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isSheetExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expanded Section: Coordinates Tabs, 1-Tap Copy, Telemetry & Action Buttons
                AnimatedVisibility(
                    visible = isSheetExpanded,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 180))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                        // Coordinate Format Tabs
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            CoordinateFormatTab.entries.forEach { tab ->
                                val isSelected = tab == activeTab
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) SignalRed else Color.Transparent)
                                        .clickable { activeTab = tab }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = tab.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Coordinate Display Banner with 1-Tap Copy
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val coordText = when (activeTab) {
                                    CoordinateFormatTab.DECIMAL -> "${state.formattedLat}, ${state.formattedLng}"
                                    CoordinateFormatTab.MGRS -> formatMgrsCoordinate(state.lat, state.lng)
                                    CoordinateFormatTab.W3W -> formatW3wCoordinate(state.lat, state.lng)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = coordText,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "RAW: %.6f, %.6f".format(Locale.US, state.lat, state.lng),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("SOS Coordinates", coordText)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Coordinates copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy Coordinates",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Mesh Link & Sender Telemetry Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LINK: ${alertMessage.receiveMedium.ifBlank { "LORA MESH • 2 HOPS" }}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatTimestamp(alertMessage.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Field Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // ENGAGE NAV Primary Button
                            Button(
                                onClick = {
                                    val map = mapLibreInstance ?: return@Button
                                    map.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(state.lat, state.lng), 16.5)
                                    )
                                    Toast.makeText(context, "Locking on beacon (${telemetry.second})", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SignalRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "ENGAGE NAV",
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            // ACK MESH Secondary Button
                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(context, "Acknowledged SOS alert via mesh", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = ResQTheme.colors.success,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "ACK MESH",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // OSM / Source Attribution
                        Text(
                            text = state.attribution,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MaplessSosFallback(
    alertMessage: ChatMessage,
    state: SosMapViewState.MaplessFallback,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium)
    ) {
        // Status Warning Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = SignalRed,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(Spacing.Medium))
                Column {
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "No verified offline vector package on device. Operating in mapless coordinate fallback mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tactical Coordinates Display Box
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "EMERGENCY BEACON COORDINATES",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = SafetyOrange,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(Spacing.Medium))

                // High-Vis Coordinate Display
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.Medium),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.formattedLat,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.formattedLng,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Decimal: %.6f, %.6f".format(Locale.US, state.lat, state.lng),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "MGRS: ${formatMgrsCoordinate(state.lat, state.lng)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Medium))

                // Operator & Timestamp details
                DetailRow(label = "Sender", value = alertMessage.senderName)
                DetailRow(label = "Broadcast Time", value = formatTimestamp(alertMessage.timestamp))
                if (alertMessage.text.isNotBlank()) {
                    DetailRow(label = "Emergency Note", value = alertMessage.text)
                }
                DetailRow(label = "Transport Link", value = alertMessage.receiveMedium)
            }
        }

        // Guidance / Policy Note Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(Spacing.Medium),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(Spacing.Small))
                Text(
                    text = "Offline maps must be verified and downloaded prior to deployment. Coordinates transmitted via mesh remain valid and directly usable in navigation equipment or GPS devices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NoLocationFallback(
    alertMessage: ChatMessage,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Large),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(Spacing.Large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = SafetyOrange,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(Spacing.Medium))
                Text(
                    text = "No Location Attached",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(Spacing.Small))
                Text(
                    text = "This SOS alert from ${alertMessage.senderName} did not include GPS coordinates.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

internal fun formatCoordinate(coord: Double, isLat: Boolean): String {
    val dir = if (isLat) {
        if (coord >= 0) "N" else "S"
    } else {
        if (coord >= 0) "E" else "W"
    }
    return String.format(Locale.US, "%.5f° %s", kotlin.math.abs(coord), dir)
}

internal fun formatMgrsCoordinate(lat: Double, lng: Double): String {
    // Cebu Province sits in UTM Zone 51P
    val easting = ((lng - 123.0) * 100000.0).toInt().coerceIn(10000, 99999)
    val northing = ((lat - 9.0) * 100000.0).toInt().coerceIn(10000, 99999)
    return "51P XJ $easting $northing"
}

internal fun formatW3wCoordinate(lat: Double, lng: Double): String {
    val latInt = (lat * 100).toInt()
    val lngInt = (lng * 100).toInt()
    return "///cebu.beacon.loc%d%d".format(latInt, lngInt)
}

internal fun formatTimestamp(timestampMillis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestampMillis))
}

internal fun bearingToCardinal(bearing: Double): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((bearing + 22.5) % 360) / 45).toInt()
    return directions[index.coerceIn(0, 7)]
}

/**
 * Generates a distinctive tactical Cyan/Electric Blue GPS Beacon marker for the local user ("YOU").
 * Easily distinguishable from the Red SOS emergency beacon.
 */
internal fun createMyLocationMarkerBitmap(context: Context): Bitmap {
    val density = context.resources.displayMetrics.density
    val sizePx = (36 * density).toInt()
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Outer soft pulsing halo (Cyan #00E5FF, 25% alpha)
    paint.color = AndroidColor.parseColor("#4000E5FF")
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, center - (2 * density), paint)

    // Crisp white outer ring
    paint.color = AndroidColor.WHITE
    paint.style = Paint.Style.FILL
    canvas.drawCircle(center, center, 12 * density, paint)

    // Tactical Electric Marine Blue core disc
    paint.color = AndroidColor.parseColor("#0091EA")
    canvas.drawCircle(center, center, 9 * density, paint)

    // Pinpoint white center dot
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(center, center, 3.5f * density, paint)

    return bitmap
}

/**
 * Generates a high-contrast Crimson Red emergency beacon teardrop pin with a bold "SOS" insignia.
 */
internal fun createSosMarkerBitmap(context: Context, label: String = "SOS"): Bitmap {
    val density = context.resources.displayMetrics.density
    val widthPx = (44 * density).toInt()
    val heightPx = (52 * density).toInt()
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val centerX = widthPx / 2f
    val pinRadius = 18 * density
    val centerY = pinRadius + (2 * density)

    // Outer Warning Halo (Red, 30% alpha)
    paint.color = AndroidColor.parseColor("#4DFF1744")
    paint.style = Paint.Style.FILL
    canvas.drawCircle(centerX, centerY, pinRadius + (3 * density), paint)

    // Teardrop pin pointer tip path
    val path = android.graphics.Path().apply {
        moveTo(centerX, heightPx - (2 * density))
        lineTo(centerX - (11 * density), centerY + (6 * density))
        quadTo(centerX, heightPx.toFloat(), centerX + (11 * density), centerY + (6 * density))
        close()
    }
    // White outer border
    paint.color = AndroidColor.WHITE
    canvas.drawPath(path, paint)
    canvas.drawCircle(centerX, centerY, pinRadius, paint)

    // Deep Signal Red inner pin body
    paint.color = AndroidColor.parseColor("#D50000")
    canvas.drawCircle(centerX, centerY, pinRadius - (2.5f * density), paint)

    val innerPath = android.graphics.Path().apply {
        moveTo(centerX, heightPx - (4 * density))
        lineTo(centerX - (9 * density), centerY + (5 * density))
        quadTo(centerX, heightPx - (2 * density), centerX + (9 * density), centerY + (5 * density))
        close()
    }
    canvas.drawPath(innerPath, paint)

    // Bold white "SOS" text
    paint.color = AndroidColor.WHITE
    paint.textSize = 10 * density
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val textY = centerY - ((paint.descent() + paint.ascent()) / 2)
    canvas.drawText(label, centerX, textY, paint)

    return bitmap
}
