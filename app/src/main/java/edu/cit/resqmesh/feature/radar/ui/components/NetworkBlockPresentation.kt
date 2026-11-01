package edu.cit.resqmesh.feature.radar.ui.components

import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.ui.model.NodeItemData
import edu.cit.resqmesh.core.ui.model.NodeKind


/** Why the link is refused: who blocked, or that a one-sided unblock may not restore it. */
internal fun blockNotice(node: NodeItemData): String? = when {
    node.isBlocked && node.blockedByThem -> "They blocked you"
    node.isBlocked -> "Your direct link is blocked"
    node.releasedLocally ->
        "You unblocked them. The link may stay refused until they unblock you too."
    else -> null
}

internal fun showsConnectAction(node: NodeItemData): Boolean =
    !node.isBlocked && node.endpointId.isNotEmpty() &&
        (node.kind == NodeKind.DISCOVERED || node.kind == NodeKind.SYNCING)

internal fun showsDisconnectAction(node: NodeItemData): Boolean =
    !node.isBlocked && (node.kind == NodeKind.DIRECT || node.kind == NodeKind.UNRESPONSIVE)

/** A handshaking or placeholder-named row has no stable identity, so blocking it would store a bogus key. */
internal fun showsBlockAction(node: NodeItemData): Boolean =
    node.isBlocked || (node.kind != NodeKind.HANDSHAKING && !NodeIdentity.isPlaceholder(node.name))

/** A blocked peer is only reachable through another phone, so say so when none is known. */
internal fun messageActionLabel(node: NodeItemData): String = when {
    node.isBlocked && node.route.size <= 2 -> "MESSAGE · WAITING FOR A RELAY"
    node.isBlocked -> "MESSAGE VIA MESH HOP"
    else -> "SEND MESSAGE"
}

internal fun blockDialogMessage(hasAlternateRoute: Boolean): String {
    val base = "Your phones will stop connecting directly. Each person must unblock to connect again. " +
        "Messages may still pass through other phones."
    return if (hasAlternateRoute) base else
        "$base No other route to them may exist right now, so messages may wait until another phone can relay."
}
