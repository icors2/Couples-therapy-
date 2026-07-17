package com.aicouples.therapy.pairing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicouples.therapy.data.model.MemberRole
import com.aicouples.therapy.data.model.RelationshipType

@Composable
fun PairingScreen(
    onPaired: (needsConsent: Boolean, relationshipId: String?) -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fade by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(500),
        label = "pairing-fade",
    )

    LaunchedEffect(state.paired) {
        if (state.paired) {
            onPaired(state.needsConsent, state.pendingConsentRelationshipId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(24.dp)
            .alpha(fade)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text(
            text = "Add a connection",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Share your code, or enter theirs. You can connect with more than one person.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Your pair code", style = MaterialTheme.typography.titleMedium)
        Text(
            text = state.myCode.ifBlank { "······" },
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Connection type", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.relationshipType == RelationshipType.COUPLES,
                onClick = { viewModel.onTypeSelected(RelationshipType.COUPLES) },
                enabled = !state.isMinor,
                label = { Text("Couples") },
            )
            FilterChip(
                selected = state.relationshipType == RelationshipType.PARENT_CHILD,
                onClick = { viewModel.onTypeSelected(RelationshipType.PARENT_CHILD) },
                label = { Text("Parent–child") },
            )
        }
        if (state.isMinor) {
            Text(
                text = "Accounts under 18 can only use parent–child connections (as Child).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.relationshipType == RelationshipType.PARENT_CHILD) {
            Text("I am the…", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.myRole == MemberRole.PARENT,
                    onClick = { viewModel.onRoleSelected(MemberRole.PARENT) },
                    enabled = !state.isMinor,
                    label = { Text("Parent") },
                )
                FilterChip(
                    selected = state.myRole == MemberRole.CHILD,
                    onClick = { viewModel.onRoleSelected(MemberRole.CHILD) },
                    label = { Text("Child") },
                )
            }
        }

        OutlinedTextField(
            value = state.partnerCodeInput,
            onValueChange = viewModel::onPartnerCodeChange,
            label = { Text("Their pair code") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = viewModel::pair,
            enabled = !state.isLoading && state.partnerCodeInput.length == 6,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (state.isLoading) "Connecting…" else "Connect")
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
