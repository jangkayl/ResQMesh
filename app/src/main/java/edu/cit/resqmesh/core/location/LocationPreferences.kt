package edu.cit.resqmesh.core.location

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User location choices. Auto-detect only keeps a recent fix ready; nothing is sent until the
 * user sends an SOS or shares a location. Own SOS ids sent without a location are remembered so
 * the sender's thread can say "not shared" instead of "acquiring".
 */
class LocationPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _autoDetectEnabled = MutableStateFlow(preferences.getBoolean(KEY_AUTO_DETECT, true))
    val autoDetectEnabled: StateFlow<Boolean> = _autoDetectEnabled.asStateFlow()

    fun setAutoDetectEnabled(enabled: Boolean) {
        if (_autoDetectEnabled.value == enabled) return
        preferences.edit().putBoolean(KEY_AUTO_DETECT, enabled).apply()
        _autoDetectEnabled.value = enabled
    }

    fun setLocationWithheld(sosId: String, withheld: Boolean) {
        val ids = withheldIds()
        val changed = if (withheld) ids.add(sosId) else ids.remove(sosId)
        if (!changed) return
        preferences.edit().putString(KEY_WITHHELD_SOS, ids.toList().takeLast(MAX_WITHHELD).joinToString(",")).apply()
    }

    fun isLocationWithheld(sosId: String): Boolean = sosId in withheldIds()

    private fun withheldIds(): LinkedHashSet<String> =
        preferences.getString(KEY_WITHHELD_SOS, null).orEmpty()
            .split(',').filterTo(LinkedHashSet()) { it.isNotBlank() }

    private companion object {
        const val PREFERENCES_NAME = "resqmesh_prefs"
        const val KEY_AUTO_DETECT = "location_auto_detect_enabled"
        const val KEY_WITHHELD_SOS = "sos_location_withheld_ids"
        const val MAX_WITHHELD = 50
    }
}
