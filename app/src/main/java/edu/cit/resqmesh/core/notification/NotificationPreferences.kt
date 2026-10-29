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

    fun setIncidentAlertsEnabled(enabled: Boolean) {
        if (_incidentAlertsEnabled.value == enabled) return
        preferences.edit().putBoolean(KEY_INCIDENT_ALERTS, enabled).apply()
        _incidentAlertsEnabled.value = enabled
    }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_prefs"
        const val KEY_INCIDENT_ALERTS = "incident_alerts_enabled"
    }
}
