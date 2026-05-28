package com.example.testresqmesh.feature.sos.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.theme.Spacing
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import android.graphics.Canvas
import android.graphics.Paint
import org.osmdroid.views.Projection
import java.io.File

@Composable
fun SosMapScreen(
    alertMessage: ChatMessage,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sosLat = alertMessage.locationLat
    val sosLng = alertMessage.locationLng

    var myLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var distanceInMeters by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        val basePath = File(context.filesDir, "osmdroid")
        basePath.mkdirs()
        val cachePath = File(basePath, "tiles")
        cachePath.mkdirs()

        Configuration.getInstance().apply {
            load(context, PreferenceManager.getDefaultSharedPreferences(context))
            userAgentValue = context.packageName // REQUIRED for osmdroid to fetch tiles!
            osmdroidBasePath = basePath
            osmdroidTileCache = cachePath
        }
        
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null && sosLat != null && sosLng != null) {
                        myLocation = GeoPoint(location.latitude, location.longitude)
                        val sosPoint = GeoPoint(sosLat, sosLng)
                        distanceInMeters = myLocation!!.distanceToAsDouble(sosPoint)
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Emergency Tracker",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (distanceInMeters != null) {
                    Text(
                        text = "Distance: ${String.format("%.1f", distanceInMeters)} meters",
                        color = Color(0xFFFF5252),
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (sosLat == null || sosLng == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No GPS coordinates available for this SOS.", color = Color.White)
            }
            return@Column
        }

        // Map View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small)
                .clip(RoundedCornerShape(16.dp)) // Force clip the map so it won't overflow the UI bounds
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.DarkGray),
                factory = { ctx ->
                    MapView(ctx).apply {
                        // FORCE Hardware Acceleration to completely eliminate lag!
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        
                        val customTileSource = XYTileSource(
                            "OpenStreetMap",
                            0, 19, 256, ".png", arrayOf(
                                "https://a.tile.openstreetmap.org/",
                                "https://b.tile.openstreetmap.org/",
                                "https://c.tile.openstreetmap.org/"
                            )
                        )
                        setTileSource(customTileSource)
                        setMultiTouchControls(true)
                        controller.setZoom(17.0)
                        
                        // Add a tactical grid overlay so it looks like a real tracker even when 100% offline with no cache
                        overlays.add(object : Overlay() {
                            val gridPaint = Paint().apply {
                                color = android.graphics.Color.parseColor("#3300FF00")
                                style = Paint.Style.STROKE
                                strokeWidth = 2f
                            }
                            override fun draw(c: Canvas, pMap: MapView, shadow: Boolean) {
                                if (shadow) return
                                val spacing = 150f
                                for (i in 0..c.width step spacing.toInt()) {
                                    c.drawLine(i.toFloat(), 0f, i.toFloat(), c.height.toFloat(), gridPaint)
                                }
                                for (i in 0..c.height step spacing.toInt()) {
                                    c.drawLine(0f, i.toFloat(), c.width.toFloat(), i.toFloat(), gridPaint)
                                }
                            }
                        })
                        
                        // Dark mode overlay to make it look tactical
                        overlays.add(object : Overlay() {
                            val darkPaint = Paint().apply {
                                color = android.graphics.Color.parseColor("#80000000") // 50% black
                                style = Paint.Style.FILL
                            }
                            override fun draw(c: Canvas, pMap: MapView, shadow: Boolean) {
                                if (shadow) return
                                c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), darkPaint)
                            }
                        })
                        
                        val sosPoint = GeoPoint(sosLat, sosLng)
                        controller.setCenter(sosPoint)

                        // Marker for the SOS Sender
                        val sosMarker = Marker(this).apply {
                            position = sosPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "SOS: ${alertMessage.senderName}"
                            // Usually you can set an icon here, we'll let it use the default osmdroid marker or we can tint it later
                        }
                        overlays.add(sosMarker)
                    }
                },
                update = { mapView ->
                    if (myLocation != null) {
                        val sosPoint = GeoPoint(sosLat, sosLng)
                        
                        // Check if my marker is already added to avoid duplicates
                        val existingMyMarker = mapView.overlays.filterIsInstance<Marker>().find { it.title == "You" }
                        if (existingMyMarker == null) {
                            val myMarker = Marker(mapView).apply {
                                position = myLocation
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "You"
                            }
                            mapView.overlays.add(myMarker)
    
                            // Draw a line connecting the two
                            val line = Polyline(mapView).apply {
                                setPoints(listOf(myLocation, sosPoint))
                                outlinePaint.color = android.graphics.Color.RED
                                outlinePaint.strokeWidth = 8f
                            }
                            mapView.overlays.add(line)
                            
                            // Adjust zoom to fit both
                            mapView.zoomToBoundingBox(
                                org.osmdroid.util.BoundingBox.fromGeoPoints(listOf(myLocation, sosPoint)),
                                true, 100
                            )
                            mapView.invalidate()
                        }
                    }
                }
            )
        }
        
        // Info panel at bottom
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "SOS FROM: ${alertMessage.senderName}",
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = alertMessage.text,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp
                )
            }
        }
    }
}
