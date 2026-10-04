package com.example.testresqmesh.feature.incident.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.ResQTheme
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.data.local.entity.IncidentOfferEntity
import com.example.testresqmesh.feature.incident.viewmodel.HelperPresentation

@Composable
fun TacticalHelperOfferCard(
    helper: HelperPresentation,
    onChoose: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onWithdraw: (() -> Unit)? = null,
    onDirectChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val offer = helper.offer
    val isSelected = helper.label.startsWith("Selected") || helper.label.startsWith("Confirmed") || helper.label.contains("Awaiting")

    val displayLabel = when {
        helper.label == "Selected · Waiting for confirmation" || helper.label == "Selected · Awaiting confirmation" -> "Selected · Awaiting"
        helper.label == "Previously confirmed helper" -> "Previously confirmed"
        helper.label == "Previously selected helper" -> "Previously selected"
        helper.label == "Selection needs review" -> "Needs review"
        else -> helper.label
    }

    val borderColor = when {
        helper.label.contains("Confirmed") -> ResQTheme.colors.success
        isSelected -> ResQTheme.colors.warning
        helper.isMe -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    val labelColor = when {
        helper.label.contains("Confirmed") -> ResQTheme.colors.success
        helper.label.contains("Selected") || helper.label.contains("Awaiting") -> ResQTheme.colors.warning
        helper.label.contains("review") -> MaterialTheme.colorScheme.error
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
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                        .then(
                            if (!helper.isMe && onDirectChat != null) {
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onDirectChat() }
                            } else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar circle with initial
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = if (helper.isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = initialLetter,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (helper.isMe) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (helper.isMe) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Your offer",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
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
                    shape = RoundedCornerShape(8.dp),
                    color = labelColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = labelColor,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                    )
                }
            }

            // Offer note in a clean tactical callout container
            if (offer.note.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (helper.isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = if (helper.isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (helper.isMe) "Your offered assistance" else "Assistance offered",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = if (helper.isMe) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = offer.note,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 19.sp
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
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyOrange,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Choose ${offer.helperName}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
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
                                .defaultMinSize(minHeight = 34.dp)
                                .height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Edit offer",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (onWithdraw != null) {
                        OutlinedButton(
                            onClick = onWithdraw,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 34.dp)
                                .height(34.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Withdraw",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
    onDirectChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val helper = HelperPresentation(
        offer = offer,
        label = when {
            isSelectedLead -> if (incidentStatus == "RESPONDING") "Confirmed helper" else "Selected · Awaiting"
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
        onDirectChat = onDirectChat,
        modifier = modifier
    )
}
