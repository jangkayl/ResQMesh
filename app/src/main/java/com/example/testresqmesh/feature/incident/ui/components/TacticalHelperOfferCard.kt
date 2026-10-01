package com.example.testresqmesh.feature.incident.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.feature.incident.viewmodel.HelperPresentation

@Composable
fun TacticalHelperOfferCard(
    helper: HelperPresentation,
    onChoose: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onWithdraw: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val offer = helper.offer
    val isSelected = helper.label.startsWith("Selected") || helper.label.startsWith("Confirmed")

    val borderColor = when {
        helper.label == "Confirmed helper" -> ResQTheme.colors.success
        isSelected -> ResQTheme.colors.warning
        helper.isMe -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    val labelColor = when {
        helper.label == "Confirmed helper" -> ResQTheme.colors.success
        helper.label.startsWith("Selected") -> ResQTheme.colors.warning
        helper.label == "Selection needs review" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val displayName = if (helper.isMe) "You" else offer.helperName
    val initialLetter = (if (helper.isMe) "Y" else offer.helperName.take(1)).uppercase()
    val relativeTime = DateUtils.getRelativeTimeSpanString(
        offer.updatedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        tonalElevation = if (isSelected) 2.dp else 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Avatar circle + Name & Time + Status label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar circle with initial
                    Surface(
                        shape = CircleShape,
                        color = if (helper.isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initialLetter,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (helper.isMe) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Offered $relativeTime",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = labelColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = helper.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = labelColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Offer note in a quote bubble container
            if (offer.note.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatQuote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = offer.note,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Permitted actions
            if (helper.canChoose && onChoose != null) {
                Button(
                    onClick = onChoose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Choose ${offer.helperName}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            } else if (helper.isMe && (onEdit != null || onWithdraw != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onEdit != null) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Edit my offer", fontSize = 13.sp)
                        }
                    }
                    if (onWithdraw != null) {
                        OutlinedButton(
                            onClick = onWithdraw,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Withdraw", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalHelperOfferCard(
    offer: IncidentOfferEntity,
    isMyOffer: Boolean,
    isSelectedLead: Boolean,
    isReporter: Boolean,
    incidentStatus: String,
    reachability: String,
    busy: Boolean,
    onSelect: (String) -> Unit,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier
) {
    val helper = HelperPresentation(
        offer = offer,
        label = when {
            isSelectedLead -> if (incidentStatus == "RESPONDING") "Confirmed helper" else "Selected · Waiting for confirmation"
            else -> "Wants to help"
        },
        isMe = isMyOffer,
        canChoose = isReporter && incidentStatus == "OPEN" && !isSelectedLead
    )
    TacticalHelperOfferCard(
        helper = helper,
        onChoose = if (helper.canChoose && !busy) { { onSelect(offer.offerId) } } else null,
        onEdit = null,
        onWithdraw = if (isMyOffer && !busy) onWithdraw else null,
        modifier = modifier
    )
}
