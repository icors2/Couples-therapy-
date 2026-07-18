package com.aicouples.therapy.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntakeScreen(
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    viewModel: IntakeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private intake") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Your answers stay private from ${state.partnerName}. " +
                    "Only the AI facilitator uses them to prepare your first session.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.isLoading) {
                Text("Loading…")
                return@Column
            }

            if (state.alreadyCompleted) {
                Text(
                    text = "You already completed intake for this connection.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(
                    onClick = onCompleted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Done")
                }
                return@Column
            }

            OutlinedTextField(
                value = state.goals,
                onValueChange = viewModel::onGoals,
                label = { Text("What are your goals?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = state.mainConcerns,
                onValueChange = viewModel::onMainConcerns,
                label = { Text("Main concerns right now") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = state.wantFromSessions,
                onValueChange = viewModel::onWantFromSessions,
                label = { Text("What do you want from these sessions?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = state.strengths,
                onValueChange = viewModel::onStrengths,
                label = { Text("Strengths you bring (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = state.communicationWish,
                onValueChange = viewModel::onCommunicationWish,
                label = { Text("How do you wish you could communicate? (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (state.showRoleNotes) {
                OutlinedTextField(
                    value = state.roleNotes,
                    onValueChange = viewModel::onRoleNotes,
                    label = { Text(state.roleNotesLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }

            OutlinedTextField(
                value = state.anythingElse,
                onValueChange = viewModel::onAnythingElse,
                label = { Text("Anything else the AI should know? (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.safetyConcern,
                    onCheckedChange = viewModel::onSafetyConcern,
                )
                Text("I have a safety concern I want the facilitator aware of")
            }
            if (state.safetyConcern) {
                OutlinedTextField(
                    value = state.safetyNote,
                    onValueChange = viewModel::onSafetyNote,
                    label = { Text("Safety note (private)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.submit(onCompleted) },
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) "Saving…" else "Submit intake")
            }
        }
    }
}
