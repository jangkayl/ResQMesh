package com.example.testresqmesh.feature.setup.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val SPLASH_DURATION_MILLIS = 3_000L

/**
 * Modern Tactical Radar Sweep Splash Screen.
 *
 * Implements an immersive peer-discovery radar sweep that illuminates offline nodes,
 * converging on the central ResQMesh logo while the mesh protocol initializes.
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MILLIS)
        onTimeout()
    }

    var loadingStarted by remember { mutableStateOf(false) }
    val loadingProgress by animateFloatAsState(
        targetValue = if (loadingStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = (SPLASH_DURATION_MILLIS - 350).toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "Splash loading progress"
    )

    LaunchedEffect(Unit) { loadingStarted = true }
    SplashContent(loadingProgress = loadingProgress)
}

@Composable
private fun SplashContent(loadingProgress: Float) {
    val loadingDescription = stringResource(R.string.splash_loading_description)
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    // Continuous Tactical Radar Sweep rotation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweepTransition")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweepAngle"
    )

    val radarPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Brand Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.resqmesh_logo),
                            contentDescription = stringResource(R.string.splash_logo_description),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "OFFLINE MESH PROTOCOL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                }
            }

            // Center Hero: Tactical Radar Sweep with Glowing Peer Nodes & Center Logo
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 1. Radar Canvas (Rings, Sweep Beam & Glowing Nodes)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxRadius = size.minDimension / 2f

                        // Concentric Range Rings
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.12f),
                            radius = maxRadius * 0.35f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.18f),
                            radius = maxRadius * 0.68f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        drawCircle(
                            color = primaryColor.copy(alpha = 0.25f),
                            radius = maxRadius,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // Crosshair lines
                        drawLine(
                            color = primaryColor.copy(alpha = 0.10f),
                            start = Offset(center.x, 0f),
                            end = Offset(center.x, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = primaryColor.copy(alpha = 0.10f),
                            start = Offset(0f, center.y),
                            end = Offset(size.width, center.y),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Rotating Radar Beam Sweep Line
                        val sweepRad = (sweepAngle * PI / 180f).toFloat()
                        val beamEnd = Offset(
                            x = center.x + maxRadius * cos(sweepRad),
                            y = center.y + maxRadius * sin(sweepRad)
                        )
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.8f), primaryColor.copy(alpha = 0.05f)),
                                start = center,
                                end = beamEnd
                            ),
                            start = center,
                            end = beamEnd,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Simulated Mesh Peer Nodes placed across the radar
                        val simulatedNodes = listOf(
                            Pair(0.48f, 45f),
                            Pair(0.72f, 130f),
                            Pair(0.85f, 210f),
                            Pair(0.60f, 315f)
                        )

                        simulatedNodes.forEach { (distFraction, nodeAngle) ->
                            val rad = (nodeAngle * PI / 180f).toFloat()
                            val nodePos = Offset(
                                x = center.x + (maxRadius * distFraction) * cos(rad),
                                y = center.y + (maxRadius * distFraction) * sin(rad)
                            )

                            // Angle difference to determine ping illumination
                            val angleDiff = ((sweepAngle - nodeAngle + 360f) % 360f)
                            val isPinged = angleDiff in 0f..55f
                            val nodeAlpha = if (isPinged) (1f - (angleDiff / 55f)) else 0.25f

                            // Outer glow if pinged
                            if (isPinged) {
                                drawCircle(
                                    color = primaryColor.copy(alpha = nodeAlpha * 0.4f),
                                    radius = 8.dp.toPx(),
                                    center = nodePos
                                )
                            }
                            // Node core
                            drawCircle(
                                color = if (isPinged) primaryColor else primaryColor.copy(alpha = 0.35f),
                                radius = if (isPinged) 3.5.dp.toPx() else 2.5.dp.toPx(),
                                center = nodePos
                            )
                        }
                    }

                    // 2. Central ResQ Core Hub
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                        border = BorderStroke(1.5.dp, primaryColor.copy(alpha = radarPulseAlpha)),
                        shadowElevation = 12.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.resqmesh_logo),
                                contentDescription = null,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.Large))

                // Tagline & Offline Mission Statement
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.onboarding_splash_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom Loading Bar & Telemetry Status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            loadingProgress < 0.35f -> "STARTING BLUETOOTH LE SCAN..."
                            loadingProgress < 0.75f -> "DISCOVERING NEARBY PEERS..."
                            else -> "PROVISIONING LOCAL MESH..."
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = primaryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(loadingProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sleek Neon Loading Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .semantics { contentDescription = loadingDescription }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loadingProgress.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.7f),
                                        primaryColor
                                    )
                                )
                            )
                    )
                }

                Text(
                    text = stringResource(R.string.splash_loading_label),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(name = "Splash — light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenLightPreview() {
    TestResQMeshTheme(darkTheme = false) { SplashContent(loadingProgress = 0.72f) }
}

@Preview(name = "Splash — dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenDarkPreview() {
    TestResQMeshTheme(darkTheme = true) { SplashContent(loadingProgress = 0.72f) }
}
