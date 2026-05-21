package com.example.testresqmesh.ui.components.layout

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import com.example.testresqmesh.utils.AppLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResQTopBar(
    title: String,
    actions: @Composable () -> Unit = {},
    navigationIcon: @Composable () -> Unit = {}
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        actions = { 
            IconButton(onClick = { AppLogger.toggleTerminal() }) {
                Icon(
                    imageVector = Icons.Default.Security, 
                    contentDescription = "Debug Terminal",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            actions() 
        },
        navigationIcon = { navigationIcon() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}
