package com.example.testresqmesh.feature.incident.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable

/** Keep small labels readable in daylight while preserving orange action surfaces. */
@Composable
internal fun incidentTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    errorLabelColor = MaterialTheme.colorScheme.onErrorContainer,
    errorSupportingTextColor = MaterialTheme.colorScheme.onErrorContainer
)
