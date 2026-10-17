package com.example.testresqmesh.data.repository.adapter

import com.example.testresqmesh.core.notification.SosAlertNotifier
import com.example.testresqmesh.data.local.entity.SosAlertEntity
import com.example.testresqmesh.data.mapper.toModel
import com.example.testresqmesh.data.repository.SosAlertSink

/** Bridges the data-layer sink to the core notifier by mapping entities to models. */
class SosAlertSinkAdapter(private val notifier: SosAlertNotifier) : SosAlertSink {
    override fun received(alert: SosAlertEntity) = notifier.received(alert.toModel())
    override fun silence(id: String) = notifier.silence(id)
    override fun ended(id: String) = notifier.ended(id)
    override fun offline() = notifier.offline()
}
