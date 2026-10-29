package edu.cit.resqmesh.core.ui.components.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

/** Whether this phone can produce a location right now, independent of the user's sharing choice. */
enum class LocationAvailability { AVAILABLE, PERMISSION_DENIED, LOCATION_OFF }

fun Context.locationAvailability(): LocationAvailability {
    val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    if (!granted) return LocationAvailability.PERMISSION_DENIED
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    return if (manager != null && !LocationManagerCompat.isLocationEnabled(manager)) {
        LocationAvailability.LOCATION_OFF
    } else {
        LocationAvailability.AVAILABLE
    }
}

/** Availability that re-checks whenever the screen resumes (for example, back from Settings). */
@Composable
fun rememberLocationAvailability(): State<LocationAvailability> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(context.locationAvailability()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = context.locationAvailability()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

/**
 * Returns an action that fixes the given problem in as few taps as possible: the system permission
 * dialog (app settings once the user has already declined it here), or the in-app "turn on location"
 * dialog with the system location settings as fallback. [onChanged] runs after the user returns.
 */
@Composable
fun rememberLocationFixAction(onChanged: () -> Unit): (LocationAvailability) -> Unit {
    val context = LocalContext.current
    var declinedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.none { it }) declinedOnce = true
        onChanged()
    }
    val resolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { onChanged() }

    return remember(context, declinedOnce) {
        { problem ->
            when (problem) {
                LocationAvailability.AVAILABLE -> Unit
                LocationAvailability.PERMISSION_DENIED -> if (declinedOnce) {
                    val appDetails = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appDetails)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } else {
                    permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
                LocationAvailability.LOCATION_OFF -> requestLocationOn(context) { resolution ->
                    resolutionLauncher.launch(IntentSenderRequest.Builder(resolution).build())
                }
            }
        }
    }
}

private fun requestLocationOn(context: Context, launch: (android.app.PendingIntent) -> Unit) {
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    val highAccuracy = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, SETTINGS_CHECK_INTERVAL_MS).build()
    val request = LocationSettingsRequest.Builder()
        .addLocationRequest(highAccuracy)
        .setAlwaysShow(true)
        .build()
    runCatching {
        LocationServices.getSettingsClient(context).checkLocationSettings(request)
            .addOnFailureListener { error ->
                val resolvable = error as? ResolvableApiException
                if (resolvable != null) runCatching { launch(resolvable.resolution) }.onFailure { openSettings() }
                else openSettings()
            }
    }.onFailure { openSettings() }
}

private const val SETTINGS_CHECK_INTERVAL_MS = 10_000L

/** Short label for the button that fixes [problem]. */
fun LocationAvailability.fixLabel(): String = when (this) {
    LocationAvailability.PERMISSION_DENIED -> "Allow"
    LocationAvailability.LOCATION_OFF -> "Turn on"
    LocationAvailability.AVAILABLE -> ""
}
