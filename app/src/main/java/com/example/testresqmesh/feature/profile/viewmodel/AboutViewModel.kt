package com.example.testresqmesh.feature.profile.viewmodel

import androidx.lifecycle.ViewModel
import com.example.testresqmesh.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AboutPillar(
    val title: String,
    val description: String,
    val badge: String
)

data class AboutUiState(
    val appName: String = "ResQMesh",
    val versionName: String = BuildConfig.VERSION_NAME,
    val versionCode: Int = BuildConfig.VERSION_CODE,
    val tagline: String = "Offline BLE messaging prototype",
    val missionStatement: String = "ResQMesh is an Android prototype for nearby text, private messaging, SOS, and voice notes. Phones use native Bluetooth Low Energy links and may relay messages through other phones without cellular data or Wi-Fi. Delivery depends on available links and routes.",
    val pillars: List<AboutPillar> = listOf(
        AboutPillar(
            title = "Native BLE Mesh Transport",
            description = "Combines native BLE advertising and scanning with GATT client/server links and an optional L2CAP payload path.",
            badge = "BLE + L2CAP"
        ),
        AboutPillar(
            title = "Private-message encryption",
            description = "Node IDs derive from an Android Keystore public key. Private messages use RSA-2048 and per-message AES-256-GCM; first-seen recipient keys are not independently verified.",
            badge = "KEYSTORE"
        ),
        AboutPillar(
            title = "Offline vector maps",
            description = "MapLibre renders installed PMTiles packages from local storage. Packages must be downloaded and activated first.",
            badge = "PMTILES"
        ),
        AboutPillar(
            title = "Emergency Distress Beacon",
            description = "SOS can include an available phone location and travel over BLE links and relays. Receipt and location depend on device and network conditions.",
            badge = "SOS BEACON"
        )
    ),
    val technicalSpecs: List<Pair<String, String>> = listOf(
        "Radio Transport" to "Native BLE 4.2+ (GATT Dual Role + L2CAP)",
        "Direct-link policy" to "Up to 3 direct neighbors; device validation pending",
        "Private-message crypto" to "RSA-2048 / AES-256-GCM; Android Keystore key",
        "Topology Protocol" to "Authoritative per-origin snapshots (30s refresh, 90s lease)",
        "Private Message Policy" to "Fail-closed (no broadcast fallback)",
        "Offline Cartography" to "MapLibre Native + Local PMTiles packages",
        "Android Support" to "Min SDK 24 (Android 7.0) · Target SDK 36",
        "Architecture Pattern" to "Single-module Kotlin, Compose, Room, Koin"
    ),
    val securityCommitments: List<String> = listOf(
        "Messages use BLE without requiring an internet connection; map packages download separately.",
        "The local RSA key is stored through Android Keystore; hardware backing varies by phone.",
        "Private sends require a usable recipient key and route, with no public-broadcast fallback. First-seen keys are not independently verified."
    )
)

class AboutViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()
}
