package com.example.testresqmesh

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testresqmesh.network.MeshNetworkManager
import com.example.testresqmesh.ui.screens.MainContainerScreen
import com.example.testresqmesh.ui.screens.setup.IdentitySetupScreen
import com.example.testresqmesh.ui.screens.setup.PermissionsScreen
import com.example.testresqmesh.ui.screens.setup.SplashScreen
import com.example.testresqmesh.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.ui.viewmodel.ChatViewModel
import com.example.testresqmesh.utils.MediaHelper

enum class AppState {
    Splash, Permissions, IdentitySetup, Main
}

class MainActivity : ComponentActivity() {

    private lateinit var networkManager: MeshNetworkManager
    private lateinit var viewModel: ChatViewModel
    private lateinit var mediaHelper: MediaHelper

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        networkManager = MeshNetworkManager(applicationContext)
        viewModel = ChatViewModel(networkManager)
        mediaHelper = MediaHelper(applicationContext)

        setContent {
            TestResQMeshTheme {
                var currentStage by remember { 
                    mutableStateOf(if (viewModel.isOnline) AppState.Main else AppState.Splash) 
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentStage) {
                        AppState.Splash -> SplashScreen {
                            currentStage = AppState.Permissions
                        }
                        AppState.Permissions -> PermissionsScreen {
                            requestRequiredPermissions()
                            checkNotificationPermission()
                            currentStage = AppState.IdentitySetup
                        }
                        AppState.IdentitySetup -> IdentitySetupScreen(viewModel) {
                            // In a real app, this would generate keys
                            viewModel.checkHardwareAndGoOnline(this, Build.MODEL, "NODE")
                            currentStage = AppState.Main
                        }
                        AppState.Main -> MainContainerScreen(viewModel = viewModel, mediaHelper = mediaHelper)
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
        viewModel.goOffline()
    }
}
