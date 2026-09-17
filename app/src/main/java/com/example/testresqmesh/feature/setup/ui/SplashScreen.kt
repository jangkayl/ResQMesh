package com.example.testresqmesh.feature.setup.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import kotlinx.coroutines.delay

private const val SPLASH_DURATION_MILLIS = 3_000L

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
            durationMillis = SPLASH_DURATION_MILLIS.toInt() - 350,
            easing = LinearEasing
        ),
        label = "Splash loading progress"
    )

    LaunchedEffect(Unit) { loadingStarted = true }
    SplashContent(loadingProgress = loadingProgress)
}

@Composable
private fun SplashContent(loadingProgress: Float) {
    val loadingDescription = stringResource(R.string.splash_loading_description)
    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Huge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(Spacing.Large))

            Column(
                modifier = Modifier.widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.resqmesh_logo),
                    contentDescription = stringResource(R.string.splash_logo_description),
                    modifier = Modifier.size(120.dp)
                )

                Spacer(Modifier.height(Spacing.ExtraLarge))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(Spacing.Small))
                Text(
                    text = stringResource(R.string.splash_tagline),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Spacing.Medium))
                Text(
                    text = stringResource(R.string.splash_description),
                    modifier = Modifier.widthIn(max = 320.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small)
            ) {
                Text(
                    text = stringResource(R.string.splash_loading_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        .semantics { contentDescription = loadingDescription }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(loadingProgress)
                            .height(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraLarge
                            )
                    )
                }
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
