package edu.cit.resqmesh.feature.sos.ui.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.network.ratelimit.SosBudgetState
import edu.cit.resqmesh.core.network.ratelimit.waitSeconds

/**
 * The raise button with the raise budget, like the chat composer: "4 of 5 left", plus " · +1 in N s" while not
 * full. At 0 it is disabled, reads " · Wait N s" and tells TalkBack "SOS available in N seconds".
 */
@Suppress("FunctionNaming")
@Composable
internal fun SosRaiseButton(budget: SosBudgetState, onCreate: () -> Unit) {
    val waiting = budget.remaining <= 0
    val seconds = budget.waitSeconds()
    val waitDescription = if (waiting) {
        pluralStringResource(R.plurals.sos_budget_available_in, seconds, seconds)
    } else {
        ""
    }
    val left = stringResource(R.string.sos_budget_left, budget.remaining, budget.capacity)
    val counter = when {
        waiting -> left + stringResource(R.string.sos_budget_wait, seconds)
        budget.remaining < budget.capacity -> left + stringResource(R.string.sos_budget_refill, seconds)
        else -> left
    }
    Button(
        onClick = onCreate,
        enabled = !waiting,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (waiting) Modifier.semantics { contentDescription = waitDescription } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SosActionRed,
            contentColor = Color.White,
            disabledContainerColor = SosActionRed.copy(alpha = DISABLED_ALPHA),
            disabledContentColor = Color.White.copy(alpha = DISABLED_ALPHA)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "BROADCAST EMERGENCY SOS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(text = counter, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private const val DISABLED_ALPHA = 0.5f
