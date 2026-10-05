package com.example.testresqmesh.feature.sos.ui.map

import android.location.Location
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.testresqmesh.core.location.LocationClient
import com.example.testresqmesh.core.map.storage.MapStyleValidator
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.ResQTheme
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
import java.util.Locale
import kotlin.math.roundToInt
import com.example.testresqmesh.feature.sos.ui.CoordinateFormatTab
import com.example.testresqmesh.feature.sos.ui.SosMapViewState
import com.example.testresqmesh.feature.sos.ui.bearingToCardinal
import com.example.testresqmesh.feature.sos.ui.map.MapExpandedDetails
import com.example.testresqmesh.feature.sos.ui.map.MapCollapsedPreview
import com.example.testresqmesh.feature.sos.ui.map.createMyLocationMarkerBitmap
import com.example.testresqmesh.feature.sos.ui.map.createSosMarkerBitmap

@Composable
internal fun MapLibreLocalMapContainer(
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

                MapCollapsedPreview(alertMessage, telemetry, isSheetExpanded) { isSheetExpanded = !isSheetExpanded }
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
                    MapExpandedDetails(state, alertMessage, activeTab, context,
                        onTabChanged = { activeTab = it },
                        onEngageNavigation = navigate@{
                            val map = mapLibreInstance ?: return@navigate
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(state.lat, state.lng), 16.5))
                            Toast.makeText(context, "Locking on beacon (${telemetry.second})", Toast.LENGTH_SHORT).show()
                        })
                }
            }
        }
    }
}
