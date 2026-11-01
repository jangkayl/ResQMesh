package edu.cit.resqmesh.feature.comms.ui.components.composer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.audio.ImageQuality
import edu.cit.resqmesh.core.network.ratelimit.ImageBudgetState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

private const val MS_PER_SECOND = 1000L

private fun ImageQuality.labelRes(): Int = when (this) {
    ImageQuality.STANDARD -> R.string.composer_image_standard
    ImageQuality.HIGH -> R.string.composer_image_high
    ImageQuality.VERY_HIGH -> R.string.composer_image_very_high
}

@Suppress("FunctionNaming", "LongParameterList")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ComposerAttachmentPreview(
    pendingImage: String?,
    pendingAudio: String?,
    onClearImage: () -> Unit,
    onClearAudio: () -> Unit,
    image: ComposerImageController,
    imageBudget: ImageBudgetState?
) {
    // 2. Pending Attachments Preview
    if (pendingImage != null || pendingAudio != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (pendingImage != null) Icons.Outlined.Image else Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (pendingImage != null) stringResource(R.string.private_chat_photo) else stringResource(R.string.private_chat_voice_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { if (pendingImage != null) onClearImage() else onClearAudio() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.private_chat_remove_attachment),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (pendingImage != null) ImageQualityRow(image, imageBudget)
            }
        }
    }
}

@Suppress("FunctionNaming")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ImageQualityRow(image: ComposerImageController, imageBudget: ImageBudgetState?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ImageQuality.entries.forEach { level ->
            val affordable = imageBudget == null || imageBudget.remaining >= level.credits
            FilterChip(
                selected = image.quality == level,
                onClick = { image.selectQuality(level) },
                enabled = !image.encoding && (affordable || image.quality == level),
                label = { Text(stringResource(level.labelRes())) }
            )
        }
    }
    val waitMs = imageBudget?.waitMsFor(image.quality.credits) ?: 0L
    val waitSeconds = ((waitMs + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt()
    val hint = when {
        waitMs > 0 -> stringResource(R.string.composer_image_wait, waitSeconds)
        image.quality != ImageQuality.STANDARD -> stringResource(R.string.composer_image_larger_hint)
        else -> null
    }
    if (hint != null) {
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
