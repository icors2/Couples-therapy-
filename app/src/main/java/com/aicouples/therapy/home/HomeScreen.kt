package com.aicouples.therapy.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        visible = true
        viewModel.refresh()
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { it / 8 }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AI Couples Therapy",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                val partnerName = state.partner?.displayName ?: "your partner"
                Text(
                    text = "Hello ${state.profile?.displayName ?: "there"}. Ready to talk with $partnerName?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.startTherapy(onStartTherapy) },
            enabled = !state.isStarting,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            Text(
                text = if (state.isStarting) "Starting…" else "Start Therapy",
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
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Waiting for ${state.partner?.displayName ?: "your partner"} to join",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Your invite stays open for 30 minutes. You can cancel it anytime.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = viewModel::cancelMyInvite) {
                Text("Cancel invite")
            }
        }

        if (state.pendingSessionId != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.pendingInvite?.title ?: "Therapy session invite",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = state.pendingInvite?.body
                    ?: "${state.partner?.displayName ?: "Your partner"} started a therapy session.",
                style = MaterialTheme.typography.bodyMedium,
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
