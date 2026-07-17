package com.aicouples.therapy.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun SettingsScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onNoConnectionsLeft: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingUnpair = state.connections.firstOrNull { it.relationship.id == state.unpairTargetId }

    if (state.unpairTargetId != null && pendingUnpair != null) {
        AlertDialog(
            onDismissRequest = { viewModel.requestUnpair(null) },
            title = { Text("Remove connection?") },
            text = {
                Text(
                    "This deletes all shared sessions and AI memory with " +
                        "${pendingUnpair.partner?.displayName ?: "this person"} (${pendingUnpair.typeLabel}). " +
                        "Other connections are kept.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.unpair(onNoConnectionsLeft) },
                    enabled = !state.isUnpairing,
                ) {
                    Text(if (state.isUnpairing) "Removing…" else "Unpair")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.requestUnpair(null) },
                    enabled = !state.isUnpairing,
                ) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = Modifier.statusBarsPadding(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Profile", style = MaterialTheme.typography.titleLarge)
            Text(state.displayName.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
            Text(state.email, style = MaterialTheme.typography.bodyMedium)
            Text("Pair code: ${state.pairCode}", style = MaterialTheme.typography.bodyMedium)

            Text("Connections", style = MaterialTheme.typography.titleLarge)
            if (state.connections.isEmpty()) {
                Text(
                    text = "No connections.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.connections.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.partnerLabel, style = MaterialTheme.typography.titleMedium)
                        Text(item.typeLabel, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = { viewModel.requestUnpair(item.relationship.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isUnpairing,
                        ) {
                            Text("Unpair")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Notifications")
                Switch(
                    checked = state.settings?.notificationsEnabled != false,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )
            }

            Text(
                text = "This app provides AI-guided communication support. It is not a substitute " +
                    "for licensed professional therapy or emergency care.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.signOut(onSignedOut) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Sign out")
            }
        }
    }
}
