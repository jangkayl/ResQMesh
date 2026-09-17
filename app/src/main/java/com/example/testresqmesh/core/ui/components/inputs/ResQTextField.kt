package com.example.testresqmesh.core.ui.components.inputs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.testresqmesh.core.ui.theme.ResQTheme

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
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = ResQTheme.colors.glassBorder,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedContainerColor = ResQTheme.colors.glassFill,
            unfocusedContainerColor = ResQTheme.colors.glassFill,
            disabledContainerColor = ResQTheme.colors.glassFill.copy(alpha = 0.48f)
        ),
        supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
    )
}
