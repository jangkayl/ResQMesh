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
import org.koin.android.ext.android.inject
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.core.ui.MainContainerScreen
import com.example.testresqmesh.feature.setup.ui.IdentitySetupScreen
import com.example.testresqmesh.feature.setup.ui.PermissionsScreen
import com.example.testresqmesh.feature.setup.ui.SplashScreen
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.core.ui.theme.AppAppearance
import com.example.testresqmesh.feature.comms.viewmodel.CommunicationViewModel
import com.example.testresqmesh.feature.radar.viewmodel.RadarViewModel
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel
import com.example.testresqmesh.core.utils.MediaHelper
import com.example.testresqmesh.core.service.MeshSessionController

import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.widget.Toast


enum class AppState {
    Splash, Permissions, IdentitySetup, Main
}

class MainActivity : ComponentActivity() {

    companion object {
        var isAppInForeground = false
    }

    private val mediaHelper: MediaHelper by inject()
    private val meshSessionController: MeshSessionController by inject()

    private var onPermissionsResult: ((Boolean) -> Unit)? = null
    private val sosDeepLinkTriggered = mutableStateOf(false)
    private val pendingChatNode = mutableStateOf<String?>(null)
    private val pendingViewMap = mutableStateOf(false)
    private val pendingSosSender = mutableStateOf<String?>(null)
    private val pendingSosText = mutableStateOf<String?>(null)

    private fun handleIntentExtras(intent: android.content.Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("EXTRA_TRIGGER_SOS", false)) {
            sosDeepLinkTriggered.value = true
            pendingSosSender.value = intent.getStringExtra("EXTRA_SOS_SENDER")
            pendingSosText.value = intent.getStringExtra("EXTRA_SOS_TEXT")
            pendingViewMap.value = true
        }
        if (intent.getBooleanExtra("EXTRA_VIEW_SOS_MAP", false)) {
            pendingViewMap.value = true
            pendingSosSender.value = intent.getStringExtra("EXTRA_SOS_SENDER")
            pendingSosText.value = intent.getStringExtra("EXTRA_SOS_TEXT")
            sosDeepLinkTriggered.value = true
        }
        val chatNode = intent.getStringExtra("EXTRA_OPEN_CHAT_NODE")
        if (!chatNode.isNullOrBlank()) {
            pendingChatNode.value = chatNode
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        onPermissionsResult?.invoke(allGranted || hasRequiredPermissions())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntentExtras(intent)
        
        // Let Compose handle window insets (stops bottom nav bar from being pushed up by keyboard)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                isAppInForeground = true
            } else if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                isAppInForeground = false
            }
        })

        setContent {
            val setupViewModel: SetupViewModel = org.koin.androidx.compose.koinViewModel()
            val radarViewModel: RadarViewModel = org.koin.androidx.compose.koinViewModel()
            val commsViewModel: CommunicationViewModel = org.koin.androidx.compose.koinViewModel()
            val walkieTalkieViewModel: com.example.testresqmesh.feature.comms.viewmodel.WalkieTalkieViewModel = org.koin.androidx.compose.koinViewModel()

            var appearance by remember { mutableStateOf(AppAppearance.load(applicationContext)) }
            TestResQMeshTheme(appearance = appearance) {
                val setupState by setupViewModel.uiState.collectAsState()
                
                // Reactive permissions and hardware states
                var permissionsState by remember { mutableStateOf(hasRequiredPermissions()) }
                var hardwareState by remember { mutableStateOf(isHardwareEnabledSafe()) }

                val hasDeepLink = remember {
                    sosDeepLinkTriggered.value || pendingChatNode.value != null || pendingViewMap.value
                }

                // Track navigation stage:
                // If opened via notification intent from closed/recent state, go directly to setup (or permissions if required).
                // If opened normally, start with Splash.
                var currentStage by remember {
                    mutableStateOf(
                        if (hasDeepLink) {
                            if (permissionsState && hardwareState) AppState.IdentitySetup else AppState.Permissions
                        } else {
                            AppState.Splash
                        }
                    )
                }

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionsState = hasRequiredPermissions()
                            hardwareState = isHardwareEnabledSafe()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // If already online (e.g. background mesh service active), allow direct progression to Main
                LaunchedEffect(setupState.isOnline) {
                    if (setupState.isOnline && currentStage != AppState.Main) {
                        currentStage = AppState.Main
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
                                currentStage = if (permissionsState && hardwareState) {
                                    AppState.IdentitySetup
                                } else {
                                    AppState.Permissions
                                }
                            }
                            AppState.Permissions -> PermissionsScreen(
                                onAllSet = { currentStage = AppState.IdentitySetup },
                                hasPermissions = permissionsState,
                                requestPermissions = {
                                    onPermissionsResult = {
                                        permissionsState = hasRequiredPermissions()
                                        hardwareState = isHardwareEnabledSafe()
                                    }
                                    requestPermissionLauncher.launch(getRequiredPermissions())
                                },
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
                                walkieTalkieViewModel = walkieTalkieViewModel,
                                mediaHelper = mediaHelper,
                                appearance = appearance,
                                onAppearanceSelected = { selected ->
                                    appearance = selected
                                    AppAppearance.save(applicationContext, selected)
                                },
                                initialChatNode = pendingChatNode.value,
                                onClearInitialChatNode = { pendingChatNode.value = null },
                                initialViewMap = pendingViewMap.value,
                                initialSosSender = pendingSosSender.value,
                                initialSosText = pendingSosText.value,
                                onClearInitialViewMap = {
                                    pendingViewMap.value = false
                                    pendingSosSender.value = null
                                    pendingSosText.value = null
                                }
                            )
                        }
                        
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
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasLocation = fineLocation || coarseLocation

        val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }

        return hasLocation && hasBluetooth
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
        meshSessionController.onActivityDestroyed(isChangingConfigurations)
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }
}
