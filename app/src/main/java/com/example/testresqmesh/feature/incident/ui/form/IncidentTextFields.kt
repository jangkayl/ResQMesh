package com.example.testresqmesh.feature.incident.ui.form

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testresqmesh.core.ui.theme.SafetyOrange
import com.example.testresqmesh.feature.incident.ui.components.incidentTextFieldColors
import androidx.compose.ui.focus.FocusState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun IncidentTitleField(
    title: String,
    showErrors: Boolean,
    titleBringIntoView: BringIntoViewRequester,
    titleFocusRequester: FocusRequester,
    onTitleChanged: (String) -> Unit,
    onTitleFocusChanged: (FocusState) -> Unit
) {
    // 3. Title (required, trimmed, 1-80 chars)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(titleBringIntoView),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val isTitleError = showErrors && (title.trim().isEmpty() || title.trim().length > 80)
        OutlinedTextField(
            value = title,
            onValueChange = { onTitleChanged(it) },
            label = { Text("Title *") },
            placeholder = { Text("Help moving an injured person") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            isError = isTitleError,
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isTitleError) {
                        Text(
                            text = if (title.trim().isEmpty()) "Title is required" else "Maximum 80 characters",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Text(
                            text = "Short descriptive title (max 80 chars)",
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${title.trim().length}/80")
                }
            },
            colors = incidentTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(titleFocusRequester)
                .onFocusChanged(onTitleFocusChanged)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun IncidentDescriptionField(
    description: String,
    descBringIntoView: BringIntoViewRequester,
    onDescriptionChanged: (String) -> Unit,
    onDescriptionFocusChanged: (FocusState) -> Unit,
    onAppendNeed: (String) -> Unit
) {
    // 4. Description (optional) + Quick Need Chips
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(descBringIntoView),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = description,
            onValueChange = { onDescriptionChanged(it) },
            label = { Text("Description") },
            placeholder = { Text("Describe what happened and the assistance needed.") },
            minLines = 3,
            maxLines = 5,
            colors = incidentTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged(onDescriptionFocusChanged)
        )

        // Quick resource / need tags
        val quickNeedTags = listOf("Stretcher", "Splint", "First aid kit", "4x4 Transport", "Oxygen", "Water")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Quick needs (tap to add):",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickNeedTags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SafetyOrange.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, SafetyOrange.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable {
                            onAppendNeed(tag)
                        }
                    ) {
                        Text(
                            text = "+ $tag",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = SafetyOrange,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
