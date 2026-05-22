package com.example.testresqmesh.core.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    private const val MAX_LOGS = 500

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _isTerminalVisible = MutableStateFlow(false)
    val isTerminalVisible: StateFlow<Boolean> = _isTerminalVisible.asStateFlow()

    fun toggleTerminal() {
        _isTerminalVisible.value = !_isTerminalVisible.value
    }

    fun d(tag: String, message: String) {
        // Log to Android Studio Logcat
        Log.d(tag, message)

        // Log to in-app terminal
        val timeStr = timeFormat.format(Date())
        val terminalMessage = "[$timeStr] $tag: $message"
        
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(terminalMessage)
        
        if (currentLogs.size > MAX_LOGS) {
            currentLogs.removeAt(0)
        }
        _logs.value = currentLogs
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
