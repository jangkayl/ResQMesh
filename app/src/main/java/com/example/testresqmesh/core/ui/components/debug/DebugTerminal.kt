package com.example.testresqmesh.core.ui.components.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.utils.AppLogger
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow

@Composable
fun DebugTerminal() {
    val isVisible by AppLogger.isTerminalVisible.collectAsState()
    if (!isVisible) return

    val logs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            var currentFilter by remember { mutableStateOf("ALL") }
            val filters = listOf("ALL", "PAIRING", "ROUTING", "E2EE", "ERRORS")
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RESQMESH // SYSTEM TERMINAL",
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row {
                    IconButton(onClick = { AppLogger.clear() }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Terminal",
                            tint = Color(0xFF00FF00)
                        )
                    }
                    IconButton(onClick = { AppLogger.toggleTerminal() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Terminal",
                            tint = Color(0xFF00FF00)
                        )
                    }
                }
            }

            Divider(color = Color(0xFF00FF00).copy(alpha = 0.5f), thickness = 1.dp)
            
            // Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters) { filter ->
                    val isSelected = currentFilter == filter
                    Text(
                        text = filter,
                        color = if (isSelected) Color.Black else Color(0xFF00FF00),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        modifier = Modifier
                            .background(if (isSelected) Color(0xFF00FF00) else Color.Transparent, RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF00FF00), RoundedCornerShape(4.dp))
                            .clickable { currentFilter = filter }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))

            val filteredLogs = logs.filter { logMsg ->
                when (currentFilter) {
                    "ALL" -> true
                    "PAIRING" -> logMsg.contains("MeshNetwork_PAIRING:") || logMsg.contains("Connection", ignoreCase = true)
                    "ROUTING" -> logMsg.contains("MeshNetwork_ROUTING:") || logMsg.contains("PayloadDispatcher:")
                    "E2EE" -> logMsg.contains("MeshNetwork_E2EE:")
                    "ERRORS" -> logMsg.contains("MeshNetwork_ERROR:") || logMsg.contains("Exception", ignoreCase = true) || logMsg.contains("failed", ignoreCase = true)
                    else -> true
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredLogs) { logMsg ->
                    Text(
                        text = logMsg,
                        color = Color(0xFF00FF00),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
