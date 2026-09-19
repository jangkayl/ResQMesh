package com.example.testresqmesh.core.network.bluetooth

/** Immediate outcome of asking the single-flight GATT client to start an outbound attempt. */
enum class BleConnectStartResult {
    STARTED,
    DEFERRED,
    REJECTED
}
