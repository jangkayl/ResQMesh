package com.example.testresqmesh.core.notification

import com.example.testresqmesh.core.model.SosAlert

/** Alerting side of SOS lifecycle events (siren, vibration, notification). */
interface SosAlertNotifier {
    fun received(alert: SosAlert)
    fun silence(id: String)
    fun ended(id: String)
    fun offline()
}
