package edu.cit.resqmesh.feature.comms.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.logging.trace.TraceGate
import edu.cit.resqmesh.core.model.ChatMessage
import edu.cit.resqmesh.core.network.SendProgress
import edu.cit.resqmesh.feature.comms.ui.DeliveryFeedback
import edu.cit.resqmesh.feature.comms.ui.privatechat.deliveryFeedback

/** What the sender's bubble says about pieces in flight. Hop stages never claim recipient delivery. */
internal enum class TransferStage { Waiting, Sending, Stalled, ReachedPhone, ReachedNextPhone, ReachedNearby, Failed }

/** Null when there is nothing to show: not mine, no live progress, or a receipt already says more. */
internal fun transferStage(message: ChatMessage, progress: SendProgress?): TransferStage? {
    val feedback = deliveryFeedback(message)
    val receipted = feedback == DeliveryFeedback.Delivered || feedback == DeliveryFeedback.Read
    return if (!message.isMine || progress == null || receipted) null else when (progress.state) {
        SendProgress.State.QUEUED -> TransferStage.Waiting
        SendProgress.State.SENDING -> TransferStage.Sending
        SendProgress.State.STALLED -> TransferStage.Stalled
        SendProgress.State.HOP_COMPLETE -> when {
            !message.isPrivate -> TransferStage.ReachedNearby
            message.isHopped -> TransferStage.ReachedNextPhone
            else -> TransferStage.ReachedPhone
        }
        SendProgress.State.FAILED -> TransferStage.Failed
    }
}

private val TransferStage.inFlight get() =
    this == TransferStage.Waiting || this == TransferStage.Sending || this == TransferStage.Stalled

/** Piece bar and stage label shown above the bubble's time row while a piece transfer is live. */
@Suppress("FunctionNaming")
@Composable
internal fun TransferProgressLine(stage: TransferStage, progress: SendProgress, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        if (stage.inFlight) {
            LinearProgressIndicator(
                progress = { progress.percent / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.25f)
            )
        }
        Text(
            text = transferLabel(stage, progress),
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 2.dp)
        )
        if (TraceGate.enabled && (progress.resent > 0 || progress.reprobes > 0)) {
            Text(
                text = stringResource(R.string.transfer_dev_detail, progress.resent, progress.reprobes),
                style = MaterialTheme.typography.labelSmall,
                color = color.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun transferLabel(stage: TransferStage, p: SendProgress): String = when (stage) {
    TransferStage.Waiting -> stringResource(R.string.transfer_waiting, p.acked, p.total)
    TransferStage.Sending -> stringResource(R.string.transfer_sending, p.acked, p.total, p.percent)
    TransferStage.Stalled -> stringResource(R.string.transfer_stalled, p.acked, p.total)
    TransferStage.ReachedPhone -> stringResource(R.string.transfer_reached_phone)
    TransferStage.ReachedNextPhone -> stringResource(R.string.transfer_reached_next_phone)
    TransferStage.ReachedNearby -> stringResource(R.string.transfer_reached_nearby)
    TransferStage.Failed -> stringResource(R.string.transfer_failed)
}
