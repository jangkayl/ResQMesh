package com.example.testresqmesh.feature.incident.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.location.DefaultLocationClient
import java.util.*

@Composable
internal fun CreateIncidentDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double?, Double?, Long?, Float?) -> Unit
) {
    val context = LocalContext.current
    val locationClient = remember { DefaultLocationClient(context) }
    var emergencyType by remember { mutableStateOf("Medical") }
    var severity by remember { mutableStateOf("Critical") }
    var description by remember { mutableStateOf("") }
    var areaDescription by remember { mutableStateOf("") }
    var location by remember { mutableStateOf<android.location.Location?>(null) }
    var isAcquiringLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    fun acquireLocation() {
        isAcquiringLocation = true
        locationError = null
        locationClient.requestPinpointLocation { result ->
            location = result
            isAcquiringLocation = false
            if (result == null) locationError = "Unable to get a location. You can still send this incident."
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) acquireLocation()
        else {
            isAcquiringLocation = false
            locationError = "Location permission was not granted."
        }
    }

    val types = listOf("Medical", "Trapped", "Fire", "Injury", "Flood", "Other")
    val severities = listOf("Moderate", "Serious", "Critical")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(Spacing.Large)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Broadcast SOS Incident",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Creates a trackable transactional emergency across the mesh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(Spacing.Medium))

                Text("Emergency Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.take(3).forEach { t ->
                        FilterChip(
                            selected = emergencyType == t,
                            onClick = { emergencyType = t },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.drop(3).forEach { t ->
                        FilterChip(
                            selected = emergencyType == t,
                            onClick = { emergencyType = t },
                            label = { Text(t, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Small))

                Text("Severity", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    severities.forEach { s ->
                        FilterChip(
                            selected = severity == s,
                            onClick = { severity = s },
                            label = { Text(s, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.Small))

                OutlinedTextField(
                    value = areaDescription,
                    onValueChange = { areaDescription = it },
                    label = { Text("Area / Landmark") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Spacing.Small))
                if (location == null) {
                    OutlinedButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            ) acquireLocation()
                            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        enabled = !isAcquiringLocation,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isAcquiringLocation) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.LocationOn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isAcquiringLocation) "Getting location…" else "Add current location")
                    }
                    locationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Current location attached", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { location = null }) { Text("Remove") }
                    }
                }

                Spacer(Modifier.height(Spacing.Small))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Situation Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(Spacing.Large))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSubmit(
                                emergencyType, severity, description, areaDescription,
                                location?.latitude, location?.longitude, location?.time,
                                location?.takeIf { it.hasAccuracy() }?.accuracy
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Broadcast SOS")
                    }
                }
            }
        }
    }
}
