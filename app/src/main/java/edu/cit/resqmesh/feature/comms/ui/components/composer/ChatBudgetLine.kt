package edu.cit.resqmesh.feature.comms.ui.components.composer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.cit.resqmesh.R
import edu.cit.resqmesh.core.network.ratelimit.ChatBudgetState

/** Whole seconds until the next send token, rounded up (at least 1). */
internal fun ChatBudgetState.waitSeconds(): Int =
    ((nextRefillInMs + MS_PER_SECOND - 1) / MS_PER_SECOND).toInt().coerceAtLeast(1)

private const val MS_PER_SECOND = 1000L

/** Composer send-budget text such as "18 of 20 left" plus " · +1 in 3 s" while the budget is not full. */
@Suppress("FunctionNaming")
@Composable
internal fun ChatBudgetLine(budget: ChatBudgetState, modifier: Modifier = Modifier) {
    val left = stringResource(R.string.chat_budget_left, budget.remaining, budget.capacity)
    val text = if (budget.remaining < budget.capacity) {
        left + stringResource(R.string.chat_budget_refill, budget.waitSeconds())
    } else {
        left
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

/** TalkBack label for a Send button that is waiting for a token. */
@Composable
internal fun chatBudgetWaitDescription(budget: ChatBudgetState): String =
    pluralStringResource(R.plurals.chat_budget_send_in, budget.waitSeconds(), budget.waitSeconds())
