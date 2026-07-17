package com.aicouples.therapy.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicouples.therapy.data.model.SessionStatus

@Composable
fun HomeScreen(
    onStartTherapy: (String) -> Unit,
    onOpenInvite: (String) -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onNeedsPairing: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.needsPairing) {
        if (state.needsPairing) onNeedsPairing()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                    )
                )
            )
            .statusBarsPadding()
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AI Couples Therapy",
                style = MaterialTheme.typography.headlineMedium,
            )
            Row {
                IconButton(onClick = onHistory) {
                    Icon(Icons.Outlined.History, contentDescription = "History")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            return
        }

        Text(
            text = "Hello, ${state.me?.displayName ?: "there"}",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.partner != null) {
                "Paired with ${state.partner!!.displayName ?: "your partner"}"
            } else {
                "Ready when you both are"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = state.pendingInvite != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Text(
                    text = state.pendingInvite?.body.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.inviteSessionId()?.let(onOpenInvite)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Review invite")
                }
            }
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }

        val active = state.activeSession
        val cta = when {
            active?.status == SessionStatus.ACTIVE -> "Resume session"
            active?.status == SessionStatus.PENDING && active.startedBy == state.me?.id ->
                "Waiting for partner"
            active?.status == SessionStatus.PENDING -> "Open invite"
            else -> "Start Therapy"
        }

        Button(
            onClick = {
                when {
                    active?.status == SessionStatus.PENDING && active.startedBy != state.me?.id ->
                        onOpenInvite(active.id)
                    active?.status == SessionStatus.ACTIVE ||
                        (active?.status == SessionStatus.PENDING && active.startedBy == state.me?.id) ->
                        onStartTherapy(active.id)
                    else -> viewModel.startTherapy(onStartTherapy)
                }
            },
            enabled = !state.starting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(if (state.starting) "Starting…" else cta)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onHistory,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text("History")
        }
        Spacer(Modifier.height(24.dp))
    }
}
