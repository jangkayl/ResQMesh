package com.example.testresqmesh.feature.sos.ui

import com.example.testresqmesh.feature.sos.ui.map.createMyLocationMarkerBitmap as createMyLocationMarkerBitmapSection
import com.example.testresqmesh.feature.sos.ui.map.createSosMarkerBitmap as createSosMarkerBitmapSection
import com.example.testresqmesh.feature.sos.ui.map.TacticalHeaderBar
import com.example.testresqmesh.feature.sos.ui.map.MaplessSosFallback as MaplessSosFallbackSection
import com.example.testresqmesh.feature.sos.ui.map.NoLocationFallback
import com.example.testresqmesh.feature.sos.ui.map.MapLibreLocalMapContainer
import android.content.Context
import androidx.compose.animation.core.*
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.testresqmesh.core.map.storage.MapStorageGuard
import com.example.testresqmesh.core.model.ChatMessage
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground

@Composable
fun SosMapScreen(
    alertMessage: ChatMessage,
    onBack: () -> Unit,
    onInstallMap: () -> Unit = {},
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
                        state = viewState,
                        onInstallMap = onInstallMap
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
fun MaplessSosFallback(
    alertMessage: ChatMessage,
    state: SosMapViewState.MaplessFallback,
    onInstallMap: () -> Unit = {},
    modifier: Modifier = Modifier
) = MaplessSosFallbackSection(alertMessage, state, onInstallMap, modifier)

/**
 * Generates a distinctive tactical Cyan/Electric Blue GPS Beacon marker for the local user ("YOU").
 * Easily distinguishable from the Red SOS emergency beacon.
 */
internal fun createMyLocationMarkerBitmap(context: Context): Bitmap = createMyLocationMarkerBitmapSection(context)

/**
 * Generates a high-contrast Crimson Red emergency beacon teardrop pin with a bold "SOS" insignia.
 */
internal fun createSosMarkerBitmap(context: Context, label: String = "SOS"): Bitmap = createSosMarkerBitmapSection(context, label)
