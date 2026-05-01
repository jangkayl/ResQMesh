package com.example.testresqmesh.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.data.models.ChatMessage
import com.example.testresqmesh.ui.viewmodel.ChatViewModel
import com.example.testresqmesh.utils.MediaHelper

@Composable
fun ChatScreen(viewModel: ChatViewModel, mediaHelper: MediaHelper) {
    // Generate the unique tag once per session
    val nodeTag = remember { java.util.UUID.randomUUID().toString().substring(0, 4).uppercase() }

    var selectedTabIndex by remember { mutableStateOf(0) }
    var activePrivateChat by remember { mutableStateOf<com.example.testresqmesh.data.models.ConnectedDevice?>(null) }

    var inputText by remember { mutableStateOf("") }
    var customNameInput by remember { mutableStateOf(android.os.Build.MODEL) }
    var pendingImageBase64 by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "ResQMesh Hub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        // --- OFFLINE SETUP UI ---
        if (!viewModel.isOnline) {
            OutlinedTextField(
                value = customNameInput,
                onValueChange = { customNameInput = it },
                label = { Text("Your Display Name") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = viewModel.teamKey,
                onValueChange = { viewModel.teamKey = it.uppercase() },
                label = { Text("Team Key (e.g., MEDIC1)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true
            )
        } else {
            Text(text = "Node ID: ${viewModel.myNodeName}", color = Color.DarkGray, modifier = Modifier.padding(top = 8.dp))
            Text(text = "Active Room: ${viewModel.teamKey}", color = Color.Blue, fontWeight = FontWeight.Bold)
        }

        Text(
            text = if (isRecording) "🔴 RECORDING VOICE MAIL..." else "Status: ${viewModel.connectionStatus}",
            color = if (isRecording) Color.Red else Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // --- GO ONLINE BUTTON ---
        if (!viewModel.isOnline) {
            Button(
                onClick = {
                    // We ONLY call the hardware check here. No UI!
                    // If the hardware is good, the ViewModel will call goOnline() for us!
                    viewModel.checkHardwareAndGoOnline(context, customNameInput, nodeTag)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                enabled = customNameInput.isNotBlank()
            ) {
                Text("Go Online (Start Mesh)", color = Color.White)
            }
        } else {
            Button(
                onClick = { viewModel.goOffline() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text("Go Offline", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- TABS & CHAT LOGIC ---
        if (viewModel.isOnline) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) { Text("Public Chat", modifier = Modifier.padding(16.dp)) }
                Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1; activePrivateChat = null }) { Text("Direct Messages", modifier = Modifier.padding(16.dp)) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTabIndex == 0) {
                // PUBLIC CHAT
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("🟢 Live in Public", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text("Total Nodes: ${viewModel.activeMeshNodes.size}", color = Color.DarkGray, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))

                val publicListState = rememberLazyListState()
                LaunchedEffect(viewModel.publicMessages.size) {
                    if (viewModel.publicMessages.isNotEmpty()) publicListState.animateScrollToItem(viewModel.publicMessages.size - 1)
                }

                LazyColumn(state = publicListState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(viewModel.publicMessages) { msg -> ChatBubble(msg, mediaHelper) }
                }

                ChatInputField(
                    inputText = inputText,
                    pendingImage = pendingImageBase64,
                    isRecording = isRecording,
                    mediaHelper = mediaHelper,
                    onTextChange = { inputText = it },
                    onImageSelected = { pendingImageBase64 = it },
                    onClearImage = { pendingImageBase64 = null },
                    onToggleRecord = {
                        if (isRecording) {
                            val audioBase64 = mediaHelper.stopRecording()
                            if (audioBase64 != null) viewModel.sendPublicMessage("🎤 Voice Mail", null, audioBase64)
                            isRecording = false
                        } else {
                            isRecording = mediaHelper.startRecording()
                        }
                    },
                    onSend = {
                        viewModel.sendPublicMessage(inputText.ifBlank { "Sent an image" }, pendingImageBase64, null)
                        inputText = ""
                        pendingImageBase64 = null
                    }
                )
            } else {
                // PRIVATE CHAT
                if (activePrivateChat == null) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Direct Physical Links:", fontWeight = FontWeight.Bold)
                        Button(onClick = { viewModel.rescan() }, enabled = !viewModel.isRescanning, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))) {
                            Text(if (viewModel.isRescanning) "Scanning..." else "Rescan", color = Color.White)
                        }
                    }

                    // --- NEW: THE RADAR / WAITING ROOM UI ---
                    if (viewModel.scannedDevices.isNotEmpty()) {
                        Text("Radar: Detected & Connecting...", fontWeight = FontWeight.Bold, color = Color(0xFFE65100), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            viewModel.scannedDevices.forEach { device ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                                    Text(text = "📡 Spotted: ${device.name}", modifier = Modifier.padding(12.dp), color = Color(0xFFE65100), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    // ----------------------------------------

                    if (viewModel.connectedDevices.isEmpty()) {
                        Text("No one is physically near you. Tap Rescan.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(viewModel.connectedDevices) { device ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { activePrivateChat = device }, colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                                    Text(text = "Chat with: ${device.name}", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Medium, color = Color.Black)
                                }
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { activePrivateChat = null }) { Text("< Back") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Private: ${activePrivateChat!!.name}", fontWeight = FontWeight.Bold)
                    }

                    val dmLog = viewModel.privateMessages[activePrivateChat!!.endpointId] ?: emptyList()
                    val privateListState = rememberLazyListState()
                    LaunchedEffect(dmLog.size) { if (dmLog.isNotEmpty()) privateListState.animateScrollToItem(dmLog.size - 1) }

                    LazyColumn(state = privateListState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(dmLog) { msg -> ChatBubble(msg, mediaHelper) }
                    }

                    ChatInputField(
                        inputText = inputText,
                        pendingImage = pendingImageBase64,
                        isRecording = isRecording,
                        mediaHelper = mediaHelper,
                        onTextChange = { inputText = it },
                        onImageSelected = { pendingImageBase64 = it },
                        onClearImage = { pendingImageBase64 = null },
                        onToggleRecord = {
                            if (isRecording) {
                                val audioBase64 = mediaHelper.stopRecording()
                                if (audioBase64 != null) viewModel.sendPrivateMessage(activePrivateChat!!, "🎤 Voice Mail", null, audioBase64)
                                isRecording = false
                            } else {
                                isRecording = mediaHelper.startRecording()
                            }
                        },
                        onSend = {
                            viewModel.sendPrivateMessage(activePrivateChat!!, inputText.ifBlank { "Sent an image" }, pendingImageBase64, null)
                            inputText = ""
                            pendingImageBase64 = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, mediaHelper: MediaHelper) {
    val bubbleColor = if (message.isMine) Color(0xFF1E88E5) else Color(0xFFE0E0E0)
    val textColor = if (message.isMine) Color.White else Color.Black
    val alignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = alignment) {
        Column(modifier = Modifier.background(bubbleColor, RoundedCornerShape(12.dp)).padding(12.dp)) {
            if (!message.isMine) {
                Text(text = message.senderName, color = Color.DarkGray, fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            }
            if (message.imageBase64 != null) {
                val bitmap = remember(message.imageBase64) { mediaHelper.decodeBase64ToBitmap(message.imageBase64) }
                bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = "Attached", modifier = Modifier.size(150.dp).padding(bottom = 4.dp)) }
            }
            if (message.audioBase64 != null) {
                Button(onClick = { mediaHelper.playVoiceMail(message.audioBase64) }, colors = ButtonDefaults.buttonColors(containerColor = if (message.isMine) Color.White else Color(0xFF1E88E5))) {
                    Text("▶️ Play Voice Note", color = if (message.isMine) Color(0xFF1E88E5) else Color.White)
                }
            } else {
                Text(text = message.text, color = textColor)
            }
        }
    }
}

@Composable
fun ChatInputField(
    inputText: String, pendingImage: String?, isRecording: Boolean, mediaHelper: MediaHelper,
    onTextChange: (String) -> Unit, onImageSelected: (String) -> Unit, onClearImage: () -> Unit,
    onToggleRecord: () -> Unit, onSend: () -> Unit
) {
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
            onImageSelected(mediaHelper.compressBitmapToBase64(bitmap))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (pendingImage != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text("📷 Image Attached", color = Color.Blue, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onClearImage, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("X") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { imagePickerLauncher.launch("image/*") }) { Text("📷") }
            IconButton(onClick = onToggleRecord) { Text(if (isRecording) "⏹️" else "🎤") }
            OutlinedTextField(value = inputText, onValueChange = onTextChange, modifier = Modifier.weight(1f), placeholder = { Text("Type...") }, enabled = !isRecording)
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSend, enabled = (inputText.isNotBlank() || pendingImage != null) && !isRecording) { Text("Send") }
        }
    }
}