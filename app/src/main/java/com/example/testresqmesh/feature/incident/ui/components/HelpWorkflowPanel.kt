package com.example.testresqmesh.feature.incident.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.data.local.entity.IncidentEntity
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.data.local.entity.DomainEventEntity
import org.json.JSONObject

@Composable
fun HelpWorkflowPanel(
    incident: IncidentEntity,
    offers: List<IncidentOfferEntity>,
    events: List<DomainEventEntity>,
    localUserId: String?,
    localSigningKey: String?,
    leadReachability: String,
    busy: Boolean,
    actionMessage: String?,
    onOffer: (String) -> Unit,
    onWithdraw: () -> Unit,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onRevoke: () -> Unit,
    onResolve: () -> Unit,
    onCancel: () -> Unit
) {
    val isReporter = incident.creatorId == localUserId
    val isSelectedHelper = incident.selectedHelperKey != null && incident.selectedHelperKey == localSigningKey
    val myOffer = offers.firstOrNull { it.helperKey == localSigningKey }
    val selectedOffer = offers.firstOrNull { it.offerId == incident.selectionOfferId }
    val terminal = incident.status == "RESOLVED" || incident.status == "CANCELLED"
    val revokedSelections = events.filter { it.applied && it.eventType == "INCIDENT_LEAD_REVOKED" }
        .mapNotNull { runCatching { JSONObject(it.payloadJson).optString("selectionId") }.getOrNull() }
        .toSet()
    val wasRevoked = !isSelectedHelper && localSigningKey != null && events.any { event ->
        if (!event.applied || event.eventType != "INCIDENT_LEAD_SELECTED") false
        else runCatching {
            val payload = JSONObject(event.payloadJson)
            payload.optString("helperKey") == localSigningKey &&
                payload.optString("selectionId") in revokedSelections
        }.getOrDefault(false)
    }
    var note by rememberSaveable(incident.incidentId) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text("HELP COORDINATION", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            when (incident.status) {
                "OPEN" -> "Open for offers. No lead helper has been selected."
                "AWAITING_HELPER" -> "Awaiting helper confirmation. Selection does not mean the helper received it."
                "RESPONDING" -> "Help in progress, based on the helper's last confirmation."
                "RESOLVED" -> "Reporter marked this request resolved on this phone."
                "CANCELLED" -> "Reporter cancelled this request on this phone."
                else -> "Current local incident state: ${incident.status}"
            }, style = MaterialTheme.typography.bodyMedium
        )
        if (wasRevoked) {
            Text("You are no longer the lead helper for this request. A previous selection was revoked.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }

        if (incident.selectionId != null) {
            Text("Selected lead: ${selectedOffer?.helperName ?: incident.primaryResponderName ?: "Helper"}",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text("Connection: $leadReachability", style = MaterialTheme.typography.bodySmall)
            if (leadReachability == "Unreachable") {
                Text("Helper currently unreachable. This does not mean they stopped helping.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (selectedOffer?.withdrawn == true) {
                Text("The selected helper withdrew their offer. The reporter can revoke this selection.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            } else if (selectedOffer != null && selectedOffer.revision != incident.selectionOfferRevision) {
                Text("The helper changed their offer after selection. The reporter should revoke and review the latest offer.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            incident.selectionConfirmedAt?.let { confirmed ->
                Text("Last confirmation: ${DateUtils.getRelativeTimeSpanString(confirmed)}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        if (actionMessage != null) {
            Text(actionMessage, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
        }

        if (!isReporter && !terminal && !(isSelectedHelper && incident.selectionId != null)) {
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 240) note = it },
                label = { Text(if (myOffer == null) "Why can you help?" else "Update your offer note") },
                supportingText = { Text("Self-reported offer • ${note.length}/240 characters") },
                minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { onOffer(note) }, enabled = !busy && note.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (myOffer == null || myOffer.withdrawn) "Offer help" else "Update offer")
            }
            if (myOffer != null && !myOffer.withdrawn) {
                OutlinedButton(onClick = onWithdraw, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Withdraw offer") }
            }
        }
        if (!isReporter && !terminal && isSelectedHelper && myOffer?.withdrawn == false) {
            OutlinedButton(onClick = onWithdraw, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Withdraw offer") }
        }

        if (incident.status == "AWAITING_HELPER" && isSelectedHelper &&
            selectedOffer?.withdrawn == false && selectedOffer.revision == incident.selectionOfferRevision) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Text("I'm helping")
                }
                OutlinedButton(onClick = onDecline, enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Decline") }
            }
        }

        HorizontalDivider()
        Text("Offers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (offers.none { !it.withdrawn }) Text("No active offers recorded on this phone.",
            style = MaterialTheme.typography.bodySmall)
        offers.filter { !it.withdrawn }.forEach { offer ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(offer.helperName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(offer.note, style = MaterialTheme.typography.bodySmall)
                if (isReporter && incident.status == "OPEN") {
                    OutlinedButton(onClick = { onSelect(offer.offerId) }, enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Select as lead helper") }
                }
            }
            HorizontalDivider()
        }

        if (isReporter && !terminal) {
            if (incident.selectionId != null) {
                Text("If you change the lead, the previous helper may not learn about it until reconnecting.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onRevoke, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Revoke lead selection") }
            }
            if (incident.status == "RESPONDING") {
                Button(onClick = onResolve, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Mark request resolved") }
            }
            OutlinedButton(onClick = onCancel, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancel request") }
        }
        Text("Updates may be delayed while phones are disconnected. Helpers are not verified responders.",
            style = MaterialTheme.typography.bodySmall)
    }
}
