package com.example.testresqmesh.feature.comms.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Location
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.location.LocationClient
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SignalRed
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.location.DefaultLocationClient
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TacticalLocationCard(
    latitude: Double,
    longitude: Double,
    senderName: String,
    noteText: String? = null,
    isMine: Boolean = false,
    onTrackOnMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locationClient = remember {
        runCatching { org.koin.java.KoinJavaComponent.getKoin().getOrNull<LocationClient>() }.getOrNull()
            ?: DefaultLocationClient(context)
    }

    var myLocation by remember { mutableStateOf<Location?>(null) }
    LaunchedEffect(Unit) {
        myLocation = locationClient.getLastKnownLocation()
    }

    // Dynamic distance & bearing calculation
    val telemetry = remember(myLocation, latitude, longitude, isMine) {
        if (isMine) {
            "MY BROADCAST POINT" to ""
        } else {
            val userLoc = myLocation
            if (userLoc != null) {
                val results = FloatArray(2)
                Location.distanceBetween(userLoc.latitude, userLoc.longitude, latitude, longitude, results)
                val dist = results[0]
                val bearing = (results[1] + 360f) % 360f
                val distStr = if (dist < 1000f) "${dist.roundToInt()} m AWAY" else String.format(Locale.US, "%.1f km AWAY", dist / 1000f)
                val dirIndex = (((bearing + 22.5) % 360) / 45).toInt()
                val cardinals = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
                val bearingStr = "BEARING %03d° %s".format(bearing.roundToInt(), cardinals[dirIndex.coerceIn(0, 7)])
                distStr to bearingStr
            } else {
                "LOCATION SHARED" to ""
            }
        }
    }

    val formattedCoords = remember(latitude, longitude) {
        val latDir = if (latitude >= 0) "N" else "S"
        val lngDir = if (longitude >= 0) "E" else "W"
        String.format(Locale.US, "%.5f° %s, %.5f° %s", kotlin.math.abs(latitude), latDir, kotlin.math.abs(longitude), lngDir)
    }

    // Animated Radar Pulse
    val infiniteTransition = rememberInfiniteTransition(label = "radarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarPulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarPulseAlpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onTrackOnMap),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Tactical Mini-Radar Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Color(0xFF090E17))
            ) {
                // Background Radar Rings Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width * 0.5f, size.height * 0.55f)

                    // Draw grid lines
                    val step = 20.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1f
                        )
                        x += step
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.04f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                        y += step
                    }

                    // Draw static radar range rings
                    drawCircle(
                        color = SignalRed.copy(alpha = 0.15f),
                        radius = 48.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = SignalRed.copy(alpha = 0.25f),
                        radius = 28.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    // Pulsing dynamic wave
                    drawCircle(
                        color = SignalRed.copy(alpha = pulseAlpha * 0.4f),
                        radius = 42.dp.toPx() * pulseScale,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Center Target Reticle & Pin
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.dp, SignalRed.copy(alpha = 0.6f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(SignalRed, CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }

                // Top-Left Badge: GPS Fix
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(if (isMine) ResQTheme.colors.success else SignalRed, CircleShape)
                        )
                        Text(
                            text = if (isMine) "MY GPS FIX" else "TARGET NODE",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Top-Right Badge: Distance Chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SignalRed.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = telemetry.first,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Bottom-Left: Bearing
                if (telemetry.second.isNotBlank()) {
                    Text(
                        text = telemetry.second,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 6.dp)
                    )
                }
            }

            // 2. Coordinate Readout & Actions Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedCoords,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "RAW: %.6f, %.6f".format(Locale.US, latitude, longitude),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Coordinates", formattedCoords)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Coordinates copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Coordinates",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (!noteText.isNullOrBlank() && !noteText.contains("I am sharing my location")) {
                    Text(
                        text = noteText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Primary Action: Track on Map
                Button(
                    onClick = onTrackOnMap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SignalRed,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "TRACK ON TACTICAL MAP",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
