package edu.cit.resqmesh.data.repository.adapter

import edu.cit.resqmesh.core.notification.SosAlertNotifier
import edu.cit.resqmesh.data.local.entity.SosAlertEntity
import edu.cit.resqmesh.data.mapper.toModel
import edu.cit.resqmesh.data.repository.SosAlertSink

/** Bridges the data-layer sink to the core notifier by mapping entities to models. */
class SosAlertSinkAdapter(private val notifier: SosAlertNotifier) : SosAlertSink {
    override fun received(alert: SosAlertEntity) = notifier.received(alert.toModel())
    override fun silence(id: String) = notifier.silence(id)
    override fun ended(id: String) = notifier.ended(id)
    override fun offline() = notifier.offline()
}
