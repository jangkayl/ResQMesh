package com.example.testresqmesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testresqmesh.data.repository.MeshRepository
import com.example.testresqmesh.network.MeshNetworkManager
import com.example.testresqmesh.ui.screens.MainContainerScreen
import com.example.testresqmesh.ui.screens.setup.IdentitySetupScreen
import com.example.testresqmesh.ui.screens.setup.PermissionsScreen
import com.example.testresqmesh.ui.screens.setup.SplashScreen
import com.example.testresqmesh.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.viewmodel.CommunicationViewModel
import com.example.testresqmesh.viewmodel.RadarViewModel
import com.example.testresqmesh.viewmodel.SetupViewModel
import com.example.testresqmesh.utils.MediaHelper

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

    private lateinit var networkManager: MeshNetworkManager
    private lateinit var repository: MeshRepository
    private lateinit var mediaHelper: MediaHelper

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        networkManager = MeshNetworkManager(applicationContext)
        repository = MeshRepository(networkManager)
        mediaHelper = MediaHelper(applicationContext)

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
                LaunchedEffect(setupState.isOnline) {
                    if (setupState.isOnline && currentStage != AppState.Main) {
                        currentStage = AppState.Main
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentStage) {
                        AppState.Splash -> SplashScreen {
                            currentStage = AppState.Permissions
                        }
                        AppState.Permissions -> PermissionsScreen {
                            requestRequiredPermissions()
                            checkNotificationPermission()
                            currentStage = AppState.IdentitySetup
                        }
                        AppState.IdentitySetup -> IdentitySetupScreen(setupViewModel) {
                            currentStage = AppState.Main
                        }
                        AppState.Main -> MainContainerScreen(
                            setupViewModel = setupViewModel,
                            radarViewModel = radarViewModel,
                            commsViewModel = commsViewModel,
                            mediaHelper = mediaHelper
                        )
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.RECORD_AUDIO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.RECORD_AUDIO)
        }
        requestPermissionLauncher.launch(perms)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkManager.stopMeshNode()
    }
}
