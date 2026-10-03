package com.example.testresqmesh.feature.sos.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

internal fun getEmergencyColor(emergencyType: String): Color {
    val upper = emergencyType.uppercase()
    return when {
        upper.contains("MED") -> Color(0xFFFF334B)
        upper.contains("FIRE") -> Color(0xFFFF6D00)
        upper.contains("TRAP") -> Color(0xFFFFB300)
        else -> Color(0xFF8B5CF6)
    }
}

internal fun getEmergencyIcon(emergencyType: String): ImageVector {
    val upper = emergencyType.uppercase()
    return when {
        upper.contains("MED") -> Icons.Default.LocalHospital
        upper.contains("FIRE") -> Icons.Default.LocalFireDepartment
        upper.contains("TRAP") -> Icons.Default.Construction
        else -> Icons.Default.Warning
    }
}
