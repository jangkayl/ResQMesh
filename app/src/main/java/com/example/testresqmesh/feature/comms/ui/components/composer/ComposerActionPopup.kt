package com.example.testresqmesh.feature.comms.ui.components.composer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.theme.ResQTheme
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ComposerActionPopup(
    isTyping: Boolean,
    isPopupExpanded: Boolean,
    isAcquiringLocation: Boolean,
    onPickImage: () -> Unit,
    onPickLocation: () -> Unit,
    onRecord: () -> Unit
) {
    // 3. Floating Glassmorphic Bubble (Pops up above when typing and '+' is clicked)
    AnimatedVisibility(
        visible = isTyping && isPopupExpanded,
        enter = fadeIn(tween(180)) + slideInVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) { it / 2 } + scaleIn(initialScale = 0.85f),
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 2 } + scaleOut(targetScale = 0.85f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, bottom = 6.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xFF0F131D).copy(alpha = 0.94f),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shadowElevation = 14.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Photo Attachment Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        onClick = onPickImage,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = stringResource(R.string.private_chat_add_image_action),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Location Sharing Button
                    Surface(
                        shape = CircleShape,
                        color = ResQTheme.colors.success.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ResQTheme.colors.success.copy(alpha = 0.35f)),
                        onClick = onPickLocation,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isAcquiringLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = ResQTheme.colors.success
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = stringResource(R.string.private_chat_share_location_action),
                                    tint = ResQTheme.colors.success,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // Voice Recording Button
                    Surface(
                        shape = CircleShape,
                        color = ResQTheme.colors.sos.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, ResQTheme.colors.sos.copy(alpha = 0.35f)),
                        onClick = onRecord,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = stringResource(R.string.private_chat_record_action),
                                tint = ResQTheme.colors.sos,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
