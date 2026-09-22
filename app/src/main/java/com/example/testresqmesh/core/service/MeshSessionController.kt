package com.example.testresqmesh.core.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.testresqmesh.data.repository.MeshRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single owner for user-requested mesh session start/stop and its foreground-service anchor.
 * Transport and routing remain owned by [MeshRepository].
 */
class MeshSessionController(
    context: Context,
    private val repository: MeshRepository
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _backgroundMeshEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_BACKGROUND_MESH_ENABLED, false)
    )
    val backgroundMeshEnabled: StateFlow<Boolean> = _backgroundMeshEnabled.asStateFlow()

    fun startNode(customName: String, nodeTag: String, teamKey: String, nodeId: String) {
        if (!repository.isOnline.value) {
            repository.startNode(customName, nodeTag, teamKey, nodeId)
        }
        if (_backgroundMeshEnabled.value) startForegroundAnchor()
    }

    fun stopNode() {
        repository.stopNode()
        stopForegroundAnchor()
    }

    fun setBackgroundMeshEnabled(enabled: Boolean) {
        if (_backgroundMeshEnabled.value == enabled) return
        preferences.edit().putBoolean(KEY_BACKGROUND_MESH_ENABLED, enabled).apply()
        _backgroundMeshEnabled.value = enabled
        if (enabled && repository.isOnline.value) {
            startForegroundAnchor()
        } else if (!enabled) {
            stopForegroundAnchor()
        }
    }

    fun onActivityDestroyed(isChangingConfigurations: Boolean) {
        if (!isChangingConfigurations && !_backgroundMeshEnabled.value) {
            repository.stopNode()
        }
    }

    private fun startForegroundAnchor() {
        ContextCompat.startForegroundService(
            appContext,
            Intent(appContext, MeshForegroundService::class.java)
                .setAction(MeshForegroundService.ACTION_START)
        )
    }

    private fun stopForegroundAnchor() {
        appContext.stopService(Intent(appContext, MeshForegroundService::class.java))
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_prefs"
        const val KEY_BACKGROUND_MESH_ENABLED = "background_mesh_enabled"
    }
}
