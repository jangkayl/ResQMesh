package edu.cit.resqmesh.core.location

/** OFF means the user turned auto-detect off; IDLE means tracking has not started. */
enum class LocationStatus {
    IDLE, ACQUIRING, READY, ERROR_DENIED, ERROR_DISABLED, OFF
}
