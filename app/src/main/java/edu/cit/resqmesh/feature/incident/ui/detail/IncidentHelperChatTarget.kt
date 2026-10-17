package edu.cit.resqmesh.feature.incident.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import edu.cit.resqmesh.core.model.NodeIdentity
import edu.cit.resqmesh.core.model.IncidentOffer

internal fun helperChatTarget(offer: IncidentOffer): String? =
    offer.helperNodeId.trim().takeIf { it.isNotEmpty() }
        ?.let { NodeIdentity.compose(offer.helperName, it) }
