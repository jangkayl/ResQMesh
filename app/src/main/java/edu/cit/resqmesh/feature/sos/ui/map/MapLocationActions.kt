package edu.cit.resqmesh.feature.sos.ui.map

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import edu.cit.resqmesh.core.ui.theme.ResQSize
import edu.cit.resqmesh.core.ui.theme.Spacing
import edu.cit.resqmesh.feature.sos.ui.copyableCoordinates

private fun copyLocation(context: Context, lat: Double, lng: Double) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Location", copyableCoordinates(lat, lng)))
    Toast.makeText(context, "Location copied", Toast.LENGTH_SHORT).show()
}

private fun openInMapsApp(context: Context, lat: Double, lng: Double, label: String) {
    val coordinates = copyableCoordinates(lat, lng).replace(" ", "")
    // Parentheses delimit the label in a geo: query, and Uri.encode leaves them as they are.
    val safeLabel = Uri.encode(label.replace("(", "").replace(")", ""))
    val uri = Uri.parse("geo:$coordinates?q=$coordinates($safeLabel)")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No maps app found. Copy the location instead.", Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun MapLocationActions(
    lat: Double,
    lng: Double,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small)
    ) {
        OutlinedButton(
            onClick = { copyLocation(context, lat, lng) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ResQSize.MinimumTouchTarget),
            contentPadding = PaddingValues(horizontal = Spacing.Small, vertical = 8.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy location")
        }
        OutlinedButton(
            onClick = { openInMapsApp(context, lat, lng, label) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ResQSize.MinimumTouchTarget),
            contentPadding = PaddingValues(horizontal = Spacing.Small, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open in maps app")
        }
    }
}
