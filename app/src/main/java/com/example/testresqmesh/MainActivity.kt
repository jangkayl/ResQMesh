package com.example.testresqmesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.core.network.NativeBleManager
import com.example.testresqmesh.core.ui.MainContainerScreen
import com.example.testresqmesh.feature.setup.ui.IdentitySetupScreen
import com.example.testresqmesh.feature.setup.ui.PermissionsScreen
import com.example.testresqmesh.feature.setup.ui.SplashScreen
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.core.utils.MediaHelper

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.widget.Toast


enum class AppState {
    Splash, Permissions, IdentitySetup, Main
}

class MeshViewModelFactory(private val repository: MeshRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SetupViewModel::class.java) -> SetupViewModel(repository) as T
            modelClass.isAssignableFrom(RadarViewModel::class.java) -> RadarViewModel(repository) as T
            modelClass.isAssignableFrom(CommunicationViewModel::class.java) -> CommunicationViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

class MainActivity : ComponentActivity() {

    companion object {
        var isAppInForeground = false
    }

    private lateinit var networkManager: NativeBleManager
    private lateinit var repository: MeshRepository
    private lateinit var mediaHelper: MediaHelper

    private var onPermissionsResult: ((Boolean) -> Unit)? = null
    private val sosDeepLinkTriggered = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        onPermissionsResult?.invoke(allGranted || hasRequiredPermissions())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Let Compose handle window insets (stops bottom nav bar from being pushed up by keyboard)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // BUG FIX: Required for OSMDroid to fetch tiles online on certain devices!
        org.osmdroid.config.Configuration.getInstance().load(
            applicationContext,
            applicationContext.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE)
        )

        networkManager = NativeBleManager(applicationContext)
        repository = MeshRepository(networkManager)
        mediaHelper = MediaHelper(applicationContext)

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                isAppInForeground = true
            } else if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                isAppInForeground = false
            }
        })

        val factory = MeshViewModelFactory(repository)

        setContent {
            val setupViewModel: SetupViewModel = viewModel(factory = factory)
            val radarViewModel: RadarViewModel = viewModel(factory = factory)
            val commsViewModel: CommunicationViewModel = viewModel(factory = factory)

            TestResQMeshTheme {
                val setupState by setupViewModel.uiState.collectAsState()
                
                // Track navigation stage - initialize with Splash to avoid black screen
                var currentStage by remember { mutableStateOf(AppState.Splash) }

                // Initial stage determination - if already online, skip to Main
                LaunchedEffect(setupState.isOnline, sosDeepLinkTriggered.value) {
                    if (sosDeepLinkTriggered.value || (setupState.isOnline && currentStage != AppState.Main)) {
                        currentStage = AppState.Main
                        sosDeepLinkTriggered.value = false
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                        when (currentStage) {
                            AppState.Splash -> SplashScreen {
                                // If fully set up, go to Identity Setup, else go to Permissions
                                currentStage = if (hasRequiredPermissions() && isHardwareEnabledSafe()) {
                                    AppState.IdentitySetup
                                } else {
                                    AppState.Permissions
                                }
                            }
                            AppState.Permissions -> PermissionsScreen(
                                onAllSet = { currentStage = AppState.IdentitySetup },
                                hasPermissions = hasRequiredPermissions(),
                                requestPermissions = { requestPermissionLauncher.launch(getRequiredPermissions()) },
                                checkHardware = { isHardwareEnabledSafe() }
                            )
                            AppState.IdentitySetup -> IdentitySetupScreen(setupViewModel) {
                                if (isHardwareEnabledSafe()) {
                                    currentStage = AppState.Main
                                } else {
                                    // Fallback to permissions if hardware turned off
                                    currentStage = AppState.Permissions
                                }
                            }
                            AppState.Main -> MainContainerScreen(
                                setupViewModel = setupViewModel,
                                radarViewModel = radarViewModel,
                                commsViewModel = commsViewModel,
                                mediaHelper = mediaHelper
                            )
                        }
                        
                        // GLOBAL DEBUG TERMINAL OVERLAY
                        com.example.testresqmesh.core.ui.components.debug.DebugTerminal()
                    }
                }
            }
        }
    }

    private fun isHardwareEnabledSafe(): Boolean {
        return try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager

            val isBluetoothOn = try { bluetoothAdapter?.isEnabled == true } catch (e: SecurityException) { false }
            val isLocationOn = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true

            isBluetoothOn && isLocationOn
        } catch (e: Exception) {
            false
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return getRequiredPermissions().all { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED 
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.addAll(listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO))
        } else {
            perms.addAll(listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.RECORD_AUDIO))
        }
        return perms.toTypedArray()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkManager.stopMeshNode()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra("EXTRA_TRIGGER_SOS", false) == true) {
            sosDeepLinkTriggered.value = true
        }
    }
}
