package com.example.testresqmesh.core.ui.components.layout

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.theme.*
import com.example.testresqmesh.core.utils.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResQTopBar(
    title: String,
    actions: @Composable () -> Unit = {},
    navigationIcon: @Composable () -> Unit = {
        Image(
            painter = painterResource(id = R.drawable.ic_resqmesh_logo),
            contentDescription = "ResQMesh Logo",
            modifier = Modifier
                .padding(start = 16.dp)
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
) {
    Column {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
            },
            actions = { 
                IconButton(onClick = { AppLogger.toggleTerminal() }) {
                    Icon(
                        imageVector = Icons.Default.Security, 
                        contentDescription = "Debug Terminal",
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                actions() 
            },
            navigationIcon = { navigationIcon() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AppBackground,
                titleContentColor = TextPrimary,
                navigationIconContentColor = TextPrimary,
                actionIconContentColor = TextPrimary
            )
        )
        // Glass-like divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.05f))
        )
    }
}
