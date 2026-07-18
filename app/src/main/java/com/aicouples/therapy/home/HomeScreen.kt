package com.aicouples.therapy.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onStartTherapy: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSession: (String) -> Unit,
    onAddConnection: () -> Unit,
    onOpenIntake: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        visible = true
        viewModel.refresh()
    }

    if (state.showConsentForId != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConsent,
            title = { Text("Parental consent required") },
            text = {
                Text(
                    "I confirm I am the parent or legal guardian of this minor. " +
                        "I consent to their use of this AI communication facilitator. " +
                        "I understand it is not licensed therapy or emergency care.",
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.grantConsentThenStart(onStartTherapy) }) {
                    Text("I consent")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConsent) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { it / 8 }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Family Therapy",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Hello ${state.profile?.displayName ?: "there"}. Choose who you'd like to talk with.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.profile?.isMinor == true && state.connections.none { it.canStartTherapy }) {
            Text(
                text = "Ask a parent/guardian to connect using your pair code, then grant consent.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Your code: ${state.profile?.pairCode.orEmpty()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text("Connections", style = MaterialTheme.typography.titleMedium)
        if (state.connections.isEmpty()) {
            Text(
                text = "No connections yet. Add a couples or parent–child link to begin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.connections.forEach { item ->
                val selected = item.relationship.id == state.selected?.relationship?.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectConnection(item.relationship.id) }
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            RoundedCornerShape(12.dp),
                        )
                        .padding(14.dp),
                ) {
                    Text(item.partnerLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            append(item.typeLabel)
                            when {
                                item.needsConsent -> append(" · consent needed")
                                item.needsMyIntake -> append(" · intake needed")
                                item.waitingPartnerIntake -> append(" · waiting on intake")
                                !item.canStartTherapy -> append(" · waiting")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onAddConnection,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Add connection")
        }

        val selected = state.selected
        if (selected?.needsMyIntake == true) {
            Button(
                onClick = { onOpenIntake(selected.relationship.id) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text("Complete intake", style = MaterialTheme.typography.titleMedium)
            }
        } else if (selected?.waitingPartnerIntake == true) {
            Text(
                text = "Waiting for ${selected.partner?.displayName ?: "them"} to finish their private intake.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { viewModel.startTherapy(onStartTherapy) },
            enabled = !state.isStarting &&
                selected != null &&
                selected.canStartTherapy &&
                !selected.needsMyIntake &&
                !selected.waitingPartnerIntake,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                text = when {
                    state.isStarting -> "Starting…"
                    selected == null -> "Start Therapy"
                    selected.needsMyIntake -> "Complete intake first"
                    selected.waitingPartnerIntake -> "Waiting for their intake"
                    else -> "Start Therapy with ${selected.partner?.displayName ?: "them"}"
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }

        OutlinedButton(
            onClick = onOpenHistory,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("History")
        }

        OutlinedButton(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Settings")
        }

        if (state.myPendingSessionId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Waiting for ${state.selected?.partner?.displayName ?: "them"} to join",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = viewModel::cancelMyInvite) {
                Text("Cancel invite")
            }
        }

        if (state.pendingSessionId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.pendingInvite?.title ?: "Therapy session invite",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewModel.joinInvite(onOpenSession) }) {
                    Text("Join Therapy")
                }
                TextButton(onClick = viewModel::declineInvite) {
                    Text("Decline")
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
