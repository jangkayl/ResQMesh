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

/** The raise button; with [raiseWaitSeconds] > 0 it shows "Wait N s" and says so to TalkBack. */
@Suppress("FunctionNaming")
@Composable
internal fun SosRaiseButton(raiseWaitSeconds: Int, onCreate: () -> Unit) {
    val waiting = raiseWaitSeconds > 0
    val waitDescription = if (waiting) {
        pluralStringResource(R.plurals.sos_budget_available_in, raiseWaitSeconds, raiseWaitSeconds)
    } else {
        ""
    }
    Button(
        onClick = onCreate,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(if (waiting) Modifier.semantics { contentDescription = waitDescription } else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SosActionRed, contentColor = Color.White),
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
            if (waiting) {
                Text(
                    text = stringResource(R.string.chat_budget_wait, raiseWaitSeconds),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
