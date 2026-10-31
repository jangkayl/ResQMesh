package edu.cit.resqmesh.feature.sos.ui.map

import edu.cit.resqmesh.core.location.LocationClientFactory
import org.koin.java.KoinJavaComponent
import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.cit.resqmesh.core.location.LocationClient
import edu.cit.resqmesh.core.map.storage.MapStyleValidator
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.ui.theme.ResQSize
import edu.cit.resqmesh.core.ui.theme.SignalRed
import edu.cit.resqmesh.core.ui.theme.Spacing
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
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import edu.cit.resqmesh.feature.sos.ui.SosMapViewState
import edu.cit.resqmesh.feature.sos.ui.describeDistance
import edu.cit.resqmesh.feature.sos.ui.mapLocationTitle
import edu.cit.resqmesh.feature.sos.ui.mapPinLabel
import edu.cit.resqmesh.feature.sos.ui.map.MapExpandedDetails
import edu.cit.resqmesh.feature.sos.ui.map.MapCollapsedPreview
import edu.cit.resqmesh.feature.sos.ui.map.createHospitalMarkerBitmap
import edu.cit.resqmesh.feature.sos.ui.map.createMyLocationMarkerBitmap
import edu.cit.resqmesh.feature.sos.ui.map.createSosMarkerBitmap

/** Style layer that draws hospitals and clinics, and the app-drawn icon that replaces its sprite. */
private const val HOSPITAL_LAYER_ID = "pois-emergency"
private const val HOSPITAL_ICON_ID = "resq-hospital"

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
        runCatching { KoinJavaComponent.getKoin().getOrNull<LocationClient>() }.getOrNull()
            ?: KoinJavaComponent.getKoin().get<LocationClientFactory>().create()
    }

    var myLocation by remember { mutableStateOf<Location?>(null) }
    var mapLibreInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapBearing by remember { mutableFloatStateOf(0f) }
    var isSheetExpanded by remember { mutableStateOf(true) }
    var isMyLocationUnavailable by remember { mutableStateOf(false) }
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var hasFramedBothLocations by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)

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
            } else if (myLocation == null) {
                isMyLocationUnavailable = true
            }
        }
    }

    // Dynamic distance & direction between the user and the location
    val distanceSummary = remember(myLocation, isMyLocationUnavailable, state.lat, state.lng) {
        val userLoc = myLocation
        if (userLoc != null) {
            val results = FloatArray(2)
            Location.distanceBetween(userLoc.latitude, userLoc.longitude, state.lat, state.lng, results)
            describeDistance(results[0], (results[1] + 360f) % 360f)
        } else if (isMyLocationUnavailable) {
            Pair("Can't find your location", "Check that location is turned on.")
        } else {
            Pair("Finding your location…", "")
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

    // Fit the camera to both the user and the target, clear of the header and bottom sheet
    fun frameBothLocations(map: MapLibreMap) {
        val userLoc = myLocation
        if (userLoc != null) {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(userLoc.latitude, userLoc.longitude))
                .include(LatLng(state.lat, state.lng))
                .build()
            val edgePx = with(density) { 56.dp.roundToPx() }
            val topPx = with(density) { 180.dp.roundToPx() }
            val bottomPx = sheetHeightPx + navigationBarBottomPx + edgePx
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, edgePx, topPx, edgePx, bottomPx))
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
                .title("You")
                .snippet("Your location")
                .icon(myLocationIcon)
        )

        // Straight line to the target in high-vis Safety Orange
        rangePolyline = map.addPolyline(
            PolylineOptions()
                .add(LatLng(userLoc.latitude, userLoc.longitude))
                .add(LatLng(state.lat, state.lng))
                .color(android.graphics.Color.parseColor("#FF9100"))
                .width(3.5f)
        )

        // The style usually loads before the first fix, so frame both once the fix arrives.
        if (!hasFramedBothLocations && map.style?.isFullyLoaded == true) {
            hasFramedBothLocations = true
            frameBothLocations(map)
        }
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

                        mapLibreMap.setStyle(styleBuilder) { style ->
                            // A new image name, so a late sprite load cannot restore the placeholder square.
                            style.addImage(HOSPITAL_ICON_ID, createHospitalMarkerBitmap(context))
                            (style.getLayer(HOSPITAL_LAYER_ID) as? SymbolLayer)
                                ?.setProperties(PropertyFactory.iconImage(HOSPITAL_ICON_ID))

                            // Target marker, distinct from the user's own marker
                            val sosIcon = IconFactory.getInstance(context)
                                .fromBitmap(createSosMarkerBitmap(context, mapPinLabel(alertMessage)))
                            mapLibreMap.addMarker(
                                MarkerOptions()
                                    .position(LatLng(state.lat, state.lng))
                                    .title("${mapLocationTitle(alertMessage)}: ${alertMessage.senderName}")
                                    .snippet(alertMessage.text.ifBlank { mapLocationTitle(alertMessage) })
                                    .icon(sosIcon)
                            )

                            // Initial camera framing
                            frameBothLocations(mapLibreMap)
                        }
                    }
                }
            }
        )

        // Top Header
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            MapHeader(alertMessage = alertMessage, onBack = onBack)

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
                        .size(ResQSize.MinimumTouchTarget)
                        .clickable(onClickLabel = "Point north") {
                            mapLibreInstance?.animateCamera(CameraUpdateFactory.bearingTo(0.0))
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

        // Floating re-center button, kept just above the bottom sheet
        FloatingActionButton(
            onClick = {
                val map = mapLibreInstance ?: return@FloatingActionButton
                frameBothLocations(map)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(
                    end = Spacing.Medium,
                    bottom = with(density) { sheetHeightPx.toDp() } + Spacing.Medium
                )
                .size(ResQSize.MinimumTouchTarget),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = SignalRed,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "Show you and this location",
                modifier = Modifier.size(24.dp)
            )
        }

        // Draggable & Collapsible Bottom Sheet
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .onSizeChanged { sheetHeightPx = it.height }
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

                MapCollapsedPreview(alertMessage, distanceSummary, isSheetExpanded) {
                    isSheetExpanded = !isSheetExpanded
                }
                // Expanded Section: location details and actions
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
                    MapExpandedDetails(
                        state = state,
                        alertMessage = alertMessage,
                        onShowOnMap = showOnMap@{
                            val map = mapLibreInstance ?: return@showOnMap
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(state.lat, state.lng), 16.5))
                        }
                    )
                }
            }
        }
    }
}
