package edu.cit.resqmesh.core.ui.components.inputs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import edu.cit.resqmesh.core.security.input.TextField
import edu.cit.resqmesh.core.security.input.TextPolicy
import edu.cit.resqmesh.core.ui.theme.ResQTheme

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun ResQTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    limit: TextField? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(if (limit != null) TextPolicy.clampLength(it, limit) else it) },
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = ResQTheme.colors.glassBorder,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
        ),
        supportingText = if (errorMessage != null || (limit != null && TextPolicy.showCounter(value.length, limit))) {
            {
                Column {
                    errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (limit != null) InputCounter(value.length, limit)
                }
            }
        } else null
    )
}

/** Small "n/max" counter, shown only within 10% of the field limit. */
@Suppress("FunctionNaming")
@Composable
fun InputCounter(length: Int, field: TextField, modifier: Modifier = Modifier) {
    if (!TextPolicy.showCounter(length, field)) return
    Text(
        text = "$length/${field.maxLength}",
        style = MaterialTheme.typography.labelSmall,
        color = if (length >= field.maxLength) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.End,
        modifier = modifier.fillMaxWidth()
    )
}
