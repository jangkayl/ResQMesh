package com.example.testresqmesh.core.network.bluetooth

import com.example.testresqmesh.core.network.TransportDispatchResult
import com.example.testresqmesh.core.network.bluetooth.state.BleLink
import java.util.UUID

/** Handler-owned bootstrap. Queue acceptance alone never admits ordinary traffic. */
class LinkIdentityAdmission(
    private val isCurrentReady: (BleLink) -> Boolean,
    private val send: (BleLink, String, String) -> TransportDispatchResult,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val admitted: (BleLink, Boolean) -> Unit,
    private val expired: (BleLink) -> Unit
) {
    private class Session(val nonce: String = UUID.randomUUID().toString()) {
        var frameId = ""
        var attempts = 0
        var peerIdentity = false
        var requiresAck = true
        var acknowledged = false
        var written = false
    }
    private val sessions = mutableMapOf<BleLink, Session>()

    fun start(link: BleLink) {
        if (!isCurrentReady(link) || link.identityAdmitted || link in sessions) return
        // Remove retired sessions even when Android never reports their disconnect.
        sessions.keys.removeAll { !isCurrentReady(it) }
        val session = Session()
        sessions[link] = session
        schedule(DEADLINE_MS) {
            if (sessions[link] === session) {
                sessions.remove(link)
                if (isCurrentReady(link) && !link.identityAdmitted) expired(link)
            }
        }
        attempt(link, session)
    }

    private fun attempt(link: BleLink, session: Session) {
        if (!owns(link, session)) return
        session.attempts++
        session.frameId = UUID.randomUUID().toString()
        val result = send(link, session.nonce, session.frameId)
        if (!result.accepted && result != TransportDispatchResult.REJECTED_INVALID_FRAME) retry(link, session)
    }

    private fun retry(link: BleLink, session: Session) {
        if (session.attempts >= MAX_ATTEMPTS) return
        val frame = session.frameId
        schedule(RETRY_MS) {
            if (owns(link, session) && session.frameId == frame) attempt(link, session)
        }
    }

    fun onFrame(endpoint: String, frameId: String, completed: Boolean) {
        val entry = sessions.entries.firstOrNull { it.key.endpoint == endpoint && it.value.frameId == frameId } ?: return
        val (link, session) = entry
        if (!owns(link, session)) return
        if (completed) {
            session.written = true
            finish(link, session)
            if (owns(link, session)) retry(link, session)
        } else retry(link, session)
    }

    fun onPeerIdentity(link: BleLink, supportsAck: Boolean) {
        val session = sessions[link] ?: return
        if (!owns(link, session)) return
        // A current-protocol peer cannot downgrade with a later legacy-shaped pulse.
        session.requiresAck = if (session.peerIdentity) session.requiresAck || supportsAck else supportsAck
        session.peerIdentity = true
        finish(link, session)
    }

    fun onAcknowledgement(link: BleLink, nonce: String) {
        val session = sessions[link] ?: return
        if (!owns(link, session) || nonce != session.nonce) return
        session.acknowledged = true
        finish(link, session)
    }

    private fun finish(link: BleLink, session: Session) {
        if (!session.written || !session.peerIdentity || (session.requiresAck && !session.acknowledged)) return
        sessions.remove(link)
        link.identityAdmitted = true
        admitted(link, !session.requiresAck)
    }

    private fun owns(link: BleLink, session: Session) = sessions[link] === session && isCurrentReady(link) && !link.identityAdmitted

    fun clear() = sessions.clear()

    companion object {
        const val VERSION = 1
        const val ACK = "IDENTITY_ACK"
        const val DEADLINE_MS = 30_000L
        private const val RETRY_MS = 5_000L
        private const val MAX_ATTEMPTS = 3
    }
}
