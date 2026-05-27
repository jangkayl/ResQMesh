package com.example.testresqmesh.core.ui.components.layout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.core.ui.theme.*

@Composable
fun ResQCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppSurface,
    elevation: Dp = 4.dp,
    cornerRadius: Dp = 24.dp,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = backgroundColor,
        shape = RoundedCornerShape(cornerRadius),
        shadowElevation = elevation,
        border = border
    ) {
        Box(content = content)
    }
}
