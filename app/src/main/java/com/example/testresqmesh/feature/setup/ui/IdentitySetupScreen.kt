package com.example.testresqmesh.feature.setup.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.testresqmesh.R
import com.example.testresqmesh.core.ui.components.buttons.ResQButton
import com.example.testresqmesh.core.ui.components.inputs.ResQTextField
import com.example.testresqmesh.core.ui.components.layout.ResQAuroraBackground
import com.example.testresqmesh.core.ui.theme.Spacing
import com.example.testresqmesh.core.ui.theme.TestResQMeshTheme
import com.example.testresqmesh.feature.setup.viewmodel.SetupViewModel

@Composable
fun IdentitySetupScreen(viewModel: SetupViewModel, onIdentityGenerated: () -> Unit) {
    val context = LocalContext.current
    var customName by remember { mutableStateOf(viewModel.getSavedName(context)) }
    var nodeTag by remember { mutableStateOf(viewModel.getSavedTag(context)) }

    IdentitySetupContent(
        customName = customName,
        onCustomNameChange = { customName = it },
        nodeTag = nodeTag,
        onNodeTagChange = { nodeTag = it },
        onIdentityGenerated = {
            viewModel.checkHardwareAndGoOnline(
                context = context,
                customName = customName.ifEmpty { Build.MODEL },
                nodeTag = nodeTag.ifEmpty { "NODE" },
                teamKey = "PUBLIC"
            )
            onIdentityGenerated()
        }
    )
}

@Composable
fun IdentitySetupContent(
    customName: String,
    onCustomNameChange: (String) -> Unit,
    nodeTag: String,
    onNodeTagChange: (String) -> Unit,
    onIdentityGenerated: () -> Unit
) {
    val scrollState = rememberScrollState()
    var submitAttempted by remember { mutableStateOf(false) }
    val isNameInvalid = submitAttempted && customName.isBlank()
    val avatarInitial = customName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    ResQAuroraBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.Large, vertical = Spacing.ExtraLarge),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(R.string.identity_step),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Spacing.Medium))
            Text(
                text = stringResource(R.string.identity_title),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Spacing.Small))
            Text(
                text = stringResource(R.string.identity_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.ExtraLarge))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = avatarInitial,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.ExtraLarge))
            ResQTextField(
                value = customName,
                onValueChange = onCustomNameChange,
                label = stringResource(R.string.identity_name_label),
                placeholder = stringResource(R.string.identity_name_placeholder),
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                isError = isNameInvalid,
                errorMessage = if (isNameInvalid) stringResource(R.string.identity_name_error) else null
            )
            Spacer(Modifier.height(Spacing.Medium))
            ResQTextField(
                value = nodeTag,
                onValueChange = { value ->
                    if (value.length <= 6 && value.all { it.isLetterOrDigit() }) {
                        onNodeTagChange(value.uppercase())
                    }
                },
                label = stringResource(R.string.identity_tag_label),
                placeholder = stringResource(R.string.identity_tag_placeholder)
            )
            Spacer(Modifier.height(Spacing.ExtraSmall))
            Text(
                text = stringResource(R.string.identity_tag_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(Spacing.Large))
            ResQButton(
                onClick = {
                    submitAttempted = true
                    if (customName.isNotBlank()) onIdentityGenerated()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.identity_start_action), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(Spacing.Small))
            Text(
                text = stringResource(R.string.identity_change_note),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "Identity setup", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun IdentitySetupPreview() {
    TestResQMeshTheme {
        IdentitySetupContent(
            customName = "Ari Santos",
            onCustomNameChange = {},
            nodeTag = "TEAM1",
            onNodeTagChange = {},
            onIdentityGenerated = {}
        )
    }
}
