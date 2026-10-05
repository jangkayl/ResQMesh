package com.example.testresqmesh.feature.incident.ui.detail

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.testresqmesh.core.model.NodeIdentity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity

internal fun helperChatTarget(offer: IncidentOfferEntity): String? =
    offer.helperNodeId.trim().takeIf { it.isNotEmpty() }
        ?.let { NodeIdentity.compose(offer.helperName, it) }
