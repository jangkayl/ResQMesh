package edu.cit.resqmesh.core.network.dispatch

import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.network.MeshPayload
import edu.cit.resqmesh.core.network.bluetooth.DirectIdentityPolicy

/** Pure inbound decisions for blocked peers; the pipeline only applies them. */
internal object BlockGatePolicy {
    /** A direct pulse from a blocked peer is dropped without disconnecting while its teardown deadline is live. */
    fun holdsPulse(deadline: Long?, now: Long): Boolean = deadline != null && now < deadline

    /**
     * Keys on the LINK's own peer, never the payload sender, so relayed traffic from a blocked origin via
     * an unblocked neighbour still passes. Placeholder link names are never treated as blocked.
     */
    fun dropsFrame(linkPeer: String?, isBlocked: (String) -> Boolean, payload: MeshPayload): Boolean {
        if (linkPeer == null || NodeIdentity.isPlaceholder(linkPeer) || !isBlocked(linkPeer)) return false
        return !DirectIdentityPolicy.allowsFrame(payload, admitted = true, blocked = true)
    }
}
