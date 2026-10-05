package com.example.testresqmesh.feature.sos.ui.broadcast

import com.example.testresqmesh.feature.sos.ui.EmergencyProfile
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MinimalistEmergencyCard(
    profile: EmergencyProfile,
    isSelected: Boolean,
    isLight: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cardColor = if (isSelected) {
        if (isLight) Color.White else Color(0xFF1C1318)
    } else {
        if (isLight) Color.White.copy(alpha = 0.90f) else Color(0xFF12151E)
    }

    val borderColor = if (isSelected) {
        profile.color
    } else {
        if (isLight) MaterialTheme.colorScheme.outlineVariant else Color(0xFF202636)
    }

    Surface(
        modifier = modifier
            .height(115.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else 1.dp,
            color = borderColor
        ),
        shadowElevation = if (isSelected) 8.dp else (if (isLight) 3.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) profile.color else (if (isLight) MaterialTheme.colorScheme.surfaceVariant else Color(0xFF1D2332))
                ) {
                    Text(
                        text = profile.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) Color.White else (if (isLight) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF8895A7)),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Icon(
                    imageVector = profile.icon,
                    contentDescription = null,
                    tint = if (isSelected) profile.color else (if (isLight) MaterialTheme.colorScheme.outline else Color(0xFF707D91)),
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = profile.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) (if (isLight) profile.color else Color.White) else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.subtitle,
                    fontSize = 10.5.sp,
                    color = if (isSelected) (if (isLight) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.75f)) else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 13.sp,
                    maxLines = 2
                )
            }
        }
    }
}
