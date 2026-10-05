package com.example.testresqmesh.feature.incident.ui.components

import com.example.testresqmesh.feature.incident.ui.coordination.CoordinationStatusBanners
import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import org.json.JSONObject

@Composable
fun IncidentCoordinationHub(
    incident: IncidentEntity,
    offers: List<IncidentOfferEntity>,
    events: List<DomainEventEntity>,
    localUserId: String?,
    localSigningKey: String?,
    leadReachability: String,
    busy: Boolean,
    actionMessage: String?,
    onOffer: (String) -> Unit,
    onWithdrawOffer: () -> Unit,
    onSelectLead: (String) -> Unit,
    onConfirmLead: () -> Unit,
    onDeclineLead: () -> Unit,
    onRevokeLead: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isReporter = incident.creatorId == localUserId
    val isSelectedHelper = incident.selectedHelperKey != null && incident.selectedHelperKey == localSigningKey
    val myOffer = offers.firstOrNull { it.helperKey == localSigningKey }
    val selectedOffer = offers.firstOrNull { it.offerId == incident.selectionOfferId }
    val terminal = incident.status == "RESOLVED" || incident.status == "CANCELLED"

    val revokedSelections = remember(events) {
        events.filter { it.applied && it.eventType == "INCIDENT_LEAD_REVOKED" }
            .mapNotNull { runCatching { JSONObject(it.payloadJson).optString("selectionId") }.getOrNull() }
            .toSet()
    }
    val wasRevoked = remember(events, isSelectedHelper, localSigningKey) {
        !isSelectedHelper && localSigningKey != null && events.any { event ->
            if (!event.applied || event.eventType != "INCIDENT_LEAD_SELECTED") false
            else runCatching {
                val payload = JSONObject(event.payloadJson)
                payload.optString("helperKey") == localSigningKey &&
                    payload.optString("selectionId") in revokedSelections
            }.getOrDefault(false)
        }
    }

    var offerNote by rememberSaveable(incident.incidentId) {
        mutableStateOf(myOffer?.takeIf { !it.withdrawn }?.note.orEmpty())
    }

    val quickCapabilities = listOf(
        "First Aid / CPR Ready",
        "En Route on Foot",
        "4x4 Vehicle Transport",
        "Heavy Rescue Tools",
        "Emergency Water / Supplies"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CoordinationStatusBanners(incident, isReporter, isSelectedHelper, selectedOffer, actionMessage, wasRevoked)

        // 2. Selected Lead Helper Dossier (if assigned or awaiting)
        if (incident.selectionId != null) {
            Text(
                text = "PRIMARY LEAD RESPONDER",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ResQTheme.colors.warningContainer.copy(alpha = 0.2f),
                border = BorderStroke(1.5.dp, ResQTheme.colors.warning),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.Large),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isLeadLocal = isSelectedHelper || leadReachability in setOf("Local Device", "Local", "This phone")
                    val leadName = selectedOffer?.helperName ?: incident.primaryResponderName ?: "Selected Helper"
                    val isAwaiting = incident.status == "AWAITING_HELPER"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isAwaiting) ResQTheme.colors.warning.copy(alpha = 0.2f) else ResQTheme.colors.success.copy(alpha = 0.2f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isAwaiting) Icons.Outlined.HourglassTop else Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = if (isAwaiting) ResQTheme.colors.warning else ResQTheme.colors.success,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = leadName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSelectedHelper) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "YOU",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 9.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isLeadLocal) "Local Device (You)" else "Mesh Link: $leadReachability",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (leadReachability == "Unreachable" && !isLeadLocal) MaterialTheme.colorScheme.error else ResQTheme.colors.success,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAwaiting) ResQTheme.colors.warning else ResQTheme.colors.success
                        ) {
                            Text(
                                text = if (isAwaiting) "PENDING" else "CONFIRMED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                ),
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    selectedOffer?.note?.let { note ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "\"$note\"",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (!isLeadLocal && leadReachability == "Unreachable") {
                        Text(
                            text = "⚠️ Helper currently unreachable via mesh. They may still be en route; state updates will sync upon reconnect.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    incident.selectionConfirmedAt?.let { confirmedAt ->
                        Text(
                            text = "Confirmed: ${DateUtils.getRelativeTimeSpanString(confirmedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Reporter revocation option
                    if (isReporter && !terminal) {
                        OutlinedButton(
                            onClick = onRevokeLead,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Outlined.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Revoke Lead Selection", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Helper Offers Section
        val activeOffers = offers.filter { !it.withdrawn }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COMMUNITY OFFERS (${activeOffers.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (activeOffers.isNotEmpty()) {
                Text(
                    text = "Tap offer to evaluate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (activeOffers.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.Large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Handshake,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "No Helper Offers Yet",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isReporter) "Your request is active across the mesh. Nearby phones with ResQMesh will see this incident."
                        else "Be the first volunteer to offer assistance! Enter your capabilities below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                activeOffers.forEach { offer ->
                    val isLead = offer.offerId == incident.selectionOfferId
                    val isMine = offer.helperKey == localSigningKey

                    TacticalHelperOfferCard(
                        offer = offer,
                        isMyOffer = isMine,
                        isSelectedLead = isLead,
                        isReporter = isReporter,
                        incidentStatus = incident.status,
                        reachability = if (isLead) leadReachability else "Mesh",
                        busy = busy,
                        onSelect = onSelectLead,
                        onWithdraw = onWithdrawOffer
                    )
                }
            }
        }

        // 4. Quick Offer Composer (Available to non-reporters when active)
        if (!isReporter && !terminal) {
            Text(
                text = if (myOffer == null || myOffer.withdrawn) "OFFER YOUR ASSISTANCE" else "UPDATE YOUR ACTIVE OFFER",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick-Insert Capability Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickCapabilities.forEach { capability ->
                            SuggestionChip(
                                onClick = {
                                    val prefix = if (offerNote.isBlank()) "" else "$offerNote, "
                                    val candidate = prefix + capability
                                    if (candidate.length <= 240) offerNote = candidate
                                },
                                label = { Text(capability, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = offerNote,
                        onValueChange = { if (it.length <= 240) offerNote = it },
                        label = { Text(if (myOffer == null || myOffer.withdrawn) "How can you assist? (Equipment, ETA, training)" else "Update offer note") },
                        placeholder = { Text("e.g. Have trauma kit, 200m away on foot, ETA 3 mins") },
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Self-reported offer")
                                Text("${offerNote.length}/240", fontFamily = FontFamily.Monospace)
                            }
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = { onOffer(offerNote) },
                        enabled = !busy && offerNote.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (myOffer == null || myOffer.withdrawn) "Submit Help Offer" else "Update My Offer",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Mesh disclaimer footer
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Coordination events are signed with device keys and relayed peer-to-peer. Helpers are civilian volunteers, not verified emergency services.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
