package com.example.testresqmesh.core.utils

import android.content.Context
import android.os.*
import com.example.testresqmesh.data.local.entity.SosAlertEntity
import com.example.testresqmesh.data.repository.SosAlertSink
import kotlinx.coroutines.*

/** App/session owner, independent of visible Compose screens. Timers are per alert. */
class SosAlertController(context: Context, private val media: MediaHelper,
    private val notifications: NotificationHelper, private val scope: CoroutineScope) : SosAlertSink {
    private val vibrator = if (Build.VERSION.SDK_INT >= 31)
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    private val ringing = mutableMapOf<String, Job>()
    override fun received(alert: SosAlertEntity) { scope.launch(Dispatchers.Main.immediate) {
        if (alert.locallySilenced || alert.ended || ringing.containsKey(alert.sosId)) return@launch
        notifications.showSosEmergencyNotification(alert.originName, "${alert.emergencyType} emergency", alert.sosId)
        media.playEmergencySiren()
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 250), 0))
            else @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 500, 250), 0)
        }
        ringing[alert.sosId] = scope.launch(Dispatchers.Main.immediate) { delay(30_000); stop(alert.sosId) }
    } }
    private fun stop(id: String) {
        ringing.remove(id)?.cancel()
        if (ringing.isEmpty()) { media.stopEmergencySiren(); vibrator.cancel() }
    }
    override fun silence(id: String) { scope.launch(Dispatchers.Main.immediate) { stop(id); notifications.clearSos(id) } }
    override fun ended(id: String) = silence(id)
    override fun offline() { scope.launch(Dispatchers.Main.immediate) { ringing.keys.toList().forEach(::stop) } }
}
