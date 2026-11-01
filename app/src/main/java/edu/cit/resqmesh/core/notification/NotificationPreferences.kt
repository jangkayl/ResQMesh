package edu.cit.resqmesh.core.notification

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User notification choices. The incident toggle gates only new-incident broadcast alerts. */
class NotificationPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _incidentAlertsEnabled = MutableStateFlow(preferences.getBoolean(KEY_INCIDENT_ALERTS, true))
    val incidentAlertsEnabled: StateFlow<Boolean> = _incidentAlertsEnabled.asStateFlow()
    private val _sosMessageAlertsEnabled = MutableStateFlow(preferences.getBoolean(KEY_SOS_MESSAGE_ALERTS, true))
    val sosMessageAlertsEnabled: StateFlow<Boolean> = _sosMessageAlertsEnabled.asStateFlow()
    private val _mutedSosThreads = MutableStateFlow(
        preferences.getStringSet(KEY_MUTED_SOS_THREADS, emptySet()).orEmpty().toSet()
    )
    val mutedSosThreads: StateFlow<Set<String>> = _mutedSosThreads.asStateFlow()

    fun setIncidentAlertsEnabled(enabled: Boolean) {
        if (_incidentAlertsEnabled.value == enabled) return
        preferences.edit().putBoolean(KEY_INCIDENT_ALERTS, enabled).apply()
        _incidentAlertsEnabled.value = enabled
    }

    /** All SOS chat notifications. New SOS alarms never depend on this. */
    fun setSosMessageAlertsEnabled(enabled: Boolean) {
        if (_sosMessageAlertsEnabled.value == enabled) return
        preferences.edit().putBoolean(KEY_SOS_MESSAGE_ALERTS, enabled).apply()
        _sosMessageAlertsEnabled.value = enabled
    }

    fun setSosThreadMuted(id: String, muted: Boolean) {
        val threads = if (muted) _mutedSosThreads.value + id else _mutedSosThreads.value - id
        preferences.edit().putStringSet(KEY_MUTED_SOS_THREADS, threads).apply()
        _mutedSosThreads.value = threads
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_prefs"
        const val KEY_INCIDENT_ALERTS = "incident_alerts_enabled"
        const val KEY_SOS_MESSAGE_ALERTS = "sos_message_alerts_enabled"
        const val KEY_MUTED_SOS_THREADS = "muted_sos_threads"
    }
}
