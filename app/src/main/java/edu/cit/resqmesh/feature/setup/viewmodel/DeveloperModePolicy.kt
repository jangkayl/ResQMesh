package edu.cit.resqmesh.feature.setup.viewmodel

/** Developer mode exists only in debug builds; a persisted flag is ignored elsewhere. */
object DeveloperModePolicy {
    private const val DEBUG_PIN = "0000"

    fun isEnabled(isDebugBuild: Boolean, storedFlag: Boolean): Boolean = isDebugBuild && storedFlag

    fun acceptsPin(isDebugBuild: Boolean, pin: String?): Boolean = isDebugBuild && pin == DEBUG_PIN
}
