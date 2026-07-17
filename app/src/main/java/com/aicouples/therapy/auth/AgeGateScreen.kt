package com.aicouples.therapy.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AgeGateScreen(
    onCompleted: () -> Unit,
    viewModel: AgeGateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Confirm your age",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "We need your date of birth to protect minors and choose the right session types. " +
                "Couples sessions are for adults 18+. Parent–child sessions with a minor require guardian consent.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.year,
                onValueChange = viewModel::onYear,
                label = { Text("YYYY") },
                modifier = Modifier.weight(1.2f),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.month,
                onValueChange = viewModel::onMonth,
                label = { Text("MM") },
                modifier = Modifier.weight(0.8f),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.day,
                onValueChange = viewModel::onDay,
                label = { Text("DD") },
                modifier = Modifier.weight(0.8f),
                singleLine = true,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.confirmed, onCheckedChange = viewModel::onConfirmed)
            Text("I confirm this date of birth is accurate")
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { viewModel.submit(onCompleted) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSubmitting) "Saving…" else "Continue")
        }
    }
}
