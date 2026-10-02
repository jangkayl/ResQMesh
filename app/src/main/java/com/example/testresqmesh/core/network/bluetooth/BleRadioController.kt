package com.example.testresqmesh.core.network.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.core.network.bluetooth.state.HandshakeRadioGate
import com.example.testresqmesh.core.utils.AppLogger
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

data class BleAdvertisement(
    val endpointId: String,
    val peerName: String,
    val nodeId: String,
    val electionScore: String,
    val directConnections: Int
)

/** Owns BLE advertising, scanning, advertisement parsing, and handshake scan suspension. */
@SuppressLint("MissingPermission")
class BleRadioController(
    context: Context,
    private val serviceUuid: UUID,
    private val isNodeActive: () -> Boolean,
    private val hasReadyConnection: () -> Boolean,
    private val onAdvertisement: (BleAdvertisement) -> Unit,
    private val onAdvertisingFailure: () -> Unit = {}
) {
    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanActive = AtomicBoolean(false)
    private val handshakeGate = HandshakeRadioGate()
    private val handler = Handler(Looper.getMainLooper())
    private var lastScanStartAt = 0L
    private var lastValidAdvertisementAt = 0L
    private var recoveryAttempts = 0
    private var nextScanAttemptAt = 0L
    private var staleScanThresholdMs = nextStaleScanThreshold()

    private val scanRecoveryRunnable = Runnable { runScanRecoveryCheck() }

    private var scanGeneration = 0L
    private var advertiseGeneration = 0L
    private var scannerOwner: BluetoothLeScanner? = null
    private var advertiserOwner: BluetoothLeAdvertiser? = null
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private val scanBudget = BleScanStartBudget()

    private fun parseAdvertisement(result: ScanResult) {
        val bytes = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
        val encoded = String(bytes, Charsets.UTF_8).replace("\u0000", "").trim()
        val parts = encoded.split("|")
        val score: String
        val connections: Int
        val displayName: String
        val nodeId: String
        when {
            parts.size >= 4 -> {
                score = parts[0]
                connections = parts[1].toIntOrNull() ?: -1
                nodeId = parts[2].trim().uppercase()
                displayName = parts.drop(3).joinToString("|")
            }
            parts.size == 3 -> {
                score = parts[0]
                connections = parts[1].toIntOrNull() ?: -1
                displayName = parts[2]
                nodeId = NodeIdentity.idOf(displayName).orEmpty()
            }
            else -> {
                AppLogger.d(
                    "BLE_MESH",
                    "Scanner: Ignored alien device ${result.device.address}. Invalid signature: $encoded"
                )
                return
            }
        }
        val peerName = if (nodeId.isNotEmpty()) {
            NodeIdentity.compose(displayName, nodeId)
        } else {
            displayName.trim()
        }
        if (peerName.isEmpty()) return
        lastValidAdvertisementAt = System.currentTimeMillis()
        recoveryAttempts = 0
        scheduleRecoveryCheck()
        onAdvertisement(
            BleAdvertisement(result.device.address, peerName, nodeId, score, connections)
        )
    }

    fun startAdvertising(
        electionScore: String,
        directConnections: Int,
        deviceName: String,
        fallbackNodeId: String
    ) {
        if (!isNodeActive()) return
        stopAdvertising()
        val advertiser = try { adapter?.bluetoothLeAdvertiser } catch (_: SecurityException) { null }
            ?: run { onAdvertisingFailure(); return }
        val generation = ++advertiseGeneration
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                handler.post {
                    if (generation == advertiseGeneration && isNodeActive()) {
                        AppLogger.d("BLE_RECOVERY", "Advertising started")
                    }
                }
            }
            override fun onStartFailure(errorCode: Int) {
                handler.post {
                    if (generation != advertiseGeneration || !isNodeActive()) return@post
                    AppLogger.d("BLE_RECOVERY", "Advertising failed code=$errorCode")
                    onAdvertisingFailure()
                }
            }
        }
        advertiserOwner = advertiser
        advertiseCallback = callback
        val nodeId = NodeIdentity.idOf(deviceName) ?: fallbackNodeId
        val prefix = "$electionScore|$directConnections|$nodeId|"
        val remainingBytes = (MAX_ADVERT_PAYLOAD_BYTES - prefix.toByteArray().size).coerceAtLeast(0)
        val displayName = truncateToBytes(NodeIdentity.displayNameOf(deviceName), remainingBytes)
        val manufacturerData = (prefix + displayName).toByteArray().let { bytes ->
            if (bytes.size <= MAX_ADVERT_PAYLOAD_BYTES) bytes
            else bytes.copyOf(MAX_ADVERT_PAYLOAD_BYTES)
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(MANUFACTURER_ID, manufacturerData)
            .build()
        try { advertiser.startAdvertising(settings, data, scanResponse, callback) }
        catch (_: Exception) { onAdvertisingFailure() }
    }

    fun startScanning() {
        if (!isNodeActive()) return
        scheduleRecoveryCheck()
        val retryWait = nextScanAttemptAt - android.os.SystemClock.elapsedRealtime()
        if (retryWait > 0L) { scheduleRecoveryCheck(retryWait); return }
        val scanner = try { adapter?.bluetoothLeScanner } catch (_: SecurityException) { null } ?: return
        if (handshakeGate.isActive() || scanActive.get()) return
        val waitMs = scanBudget.delayUntilAllowed(android.os.SystemClock.elapsedRealtime())
        if (waitMs > 0) { scheduleRecoveryCheck(waitMs); return }
        if (!scanActive.compareAndSet(false, true)) return
        val generation = ++scanGeneration
        val callback = object : ScanCallback() {
            override fun onScanFailed(errorCode: Int) {
                handler.post {
                    if (generation != scanGeneration || !isNodeActive()) return@post
                    scanActive.set(false)
                    AppLogger.d("BLE_RECOVERY", "Scanner failed code=$errorCode")
                    scheduleFailedScanRetry()
                }
            }
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handler.post {
                    if (generation == scanGeneration && scanActive.get() && isNodeActive()) {
                        try { parseAdvertisement(result) } catch (_: SecurityException) {
                            AppLogger.d("BLE_RECOVERY", "Scan result ignored after Bluetooth access changed")
                        }
                    }
                }
            }
        }
        scannerOwner = scanner
        scanCallback = callback
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        try {
            scanBudget.recordStart(android.os.SystemClock.elapsedRealtime())
            scanner.startScan(filters, settings, callback)
            lastScanStartAt = System.currentTimeMillis()
            staleScanThresholdMs = nextStaleScanThreshold()
        } catch (error: Exception) {
            scanActive.set(false)
            AppLogger.d("BLE_MESH", "Scanner start failed: ${error.message}")
            scheduleFailedScanRetry()
        }
    }

    fun beginHandshake(owner: String) {
        if (!handshakeGate.begin(owner)) return
        stopScanning()
        AppLogger.d("BLE_MESH", "Radio handshake gate acquired by $owner; scanning paused")
    }

    fun finishHandshake(owner: String, reason: String) {
        if (!handshakeGate.finish(owner)) return
        AppLogger.d("BLE_MESH", "Radio handshake gate released by $owner ($reason); scanning resumed")
        if (isNodeActive()) startScanning()
    }

    fun isHandshakeActive(): Boolean = handshakeGate.isActive()

    fun activeHandshakeInfo(now: Long = System.currentTimeMillis()): Pair<String?, Long> =
        handshakeGate.activeOwnerInfo(now)

    fun rescan() {
        recoveryAttempts = 0
        stopScanning()
        startScanning()
    }

    fun reconcileHandshakeOwners(isLiveOwner: (String) -> Boolean) {
        if (handshakeGate.reconcileOwners(isLiveOwner)) {
            AppLogger.d("BLE_MESH", "Removed orphaned radio handshake owners; scanning resumed")
            startScanning()
        }
    }

    fun stop() {
        handler.removeCallbacks(scanRecoveryRunnable)
        stopAdvertising()
        stopScanning()
        handshakeGate.clear()
        nextScanAttemptAt = 0L
        lastScanStartAt = 0L
        lastValidAdvertisementAt = 0L
        recoveryAttempts = 0
    }

    private fun runScanRecoveryCheck() {
        if (!isNodeActive()) return
        if (hasReadyConnection()) {
            recoveryAttempts = 0
            if (!scanActive.get() && !handshakeGate.isActive()) startScanning() else scheduleRecoveryCheck()
            return
        }
        if (handshakeGate.isActive()) {
            scheduleRecoveryCheck()
            return
        }

        val now = System.currentTimeMillis()
        val lastProgressAt = maxOf(lastScanStartAt, lastValidAdvertisementAt)
        val millisSinceProgress = if (lastProgressAt > 0L) now - lastProgressAt else Long.MAX_VALUE
        val scanIsStale = scanActive.get() &&
            millisSinceProgress >= staleScanThresholdMs

        if (BleScanRecoveryPolicy.shouldRestart(
                nodeActive = true,
                hasReadyConnection = false,
                handshakeActive = false,
                scanActive = scanActive.get(),
                millisSinceScanProgress = millisSinceProgress,
                staleAfterMs = staleScanThresholdMs
            )) {
            recoveryAttempts++
            AppLogger.d(
                "BLE_MESH",
                "Zero-ready-peer scan watchdog restarting discovery (attempt=$recoveryAttempts, stale=$scanIsStale)"
            )
            stopScanning()
            startScanning()
        } else {
            scheduleRecoveryCheck()
        }
    }

    private fun scheduleFailedScanRetry() {
        if (!isNodeActive()) return
        recoveryAttempts++
        val delay = BleScanRecoveryPolicy.retryDelay(recoveryAttempts) +
            Random.nextLong(BleScanRecoveryPolicy.RETRY_JITTER_MS + 1L)
        nextScanAttemptAt = android.os.SystemClock.elapsedRealtime() + delay
        scheduleRecoveryCheck(delay)
    }

    private fun scheduleRecoveryCheck(delayMs: Long = BleScanRecoveryPolicy.HEALTH_CHECK_MS) {
        handler.removeCallbacks(scanRecoveryRunnable)
        if (isNodeActive()) handler.postDelayed(scanRecoveryRunnable, delayMs)
    }

    private fun nextStaleScanThreshold(): Long = BleScanRecoveryPolicy.STALE_SCAN_AFTER_MS +
        Random.nextLong(BleScanRecoveryPolicy.RETRY_JITTER_MS + 1L)

    private fun stopScanning() {
        scanGeneration++
        scanActive.set(false)
        val callback = scanCallback
        scanCallback = null
        try { if (callback != null) scannerOwner?.stopScan(callback) } catch (_: Exception) {}
        scannerOwner = null
    }

    private fun stopAdvertising() {
        advertiseGeneration++
        val callback = advertiseCallback
        advertiseCallback = null
        try { if (callback != null) advertiserOwner?.stopAdvertising(callback) } catch (_: Exception) {}
        advertiserOwner = null
    }

    private fun truncateToBytes(value: String, maxBytes: Int): String {
        if (maxBytes <= 0) return ""
        if (value.toByteArray().size <= maxBytes) return value
        var end = value.length
        while (end > 0) {
            val candidate = value.substring(0, end)
            if (candidate.toByteArray().size <= maxBytes) return candidate
            end--
        }
        return ""
    }

    private companion object {
        const val MANUFACTURER_ID = 0xFFFF
        const val MAX_ADVERT_PAYLOAD_BYTES = 26
    }
}
