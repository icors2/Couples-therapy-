package com.aicouples.therapy.ui.pairing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicouples.therapy.ui.theme.MotionTokens

@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val codeScale by animateFloatAsState(
        targetValue = if (state.myCode.isNotBlank()) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = MotionTokens.SoftDamping,
            stiffness = MotionTokens.SoftStiffness,
        ),
        label = "codeScale",
    )

    LaunchedEffect(state.paired) {
        if (state.paired) onPaired()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
            .statusBarsPadding()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Pair with your partner",
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = "Share your code, or enter theirs. Only one partner can be linked.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        )

        Spacer(Modifier.height(12.dp))
        Text("Your pair code", style = MaterialTheme.typography.labelLarge)
        Text(
            text = state.myCode.ifBlank { "······" },
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .scale(codeScale),
            color = MaterialTheme.colorScheme.primary,
        )

        OutlinedTextField(
            value = state.partnerCodeInput,
            onValueChange = viewModel::onCodeChange,
            label = { Text("Partner code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = viewModel::pair,
            enabled = !state.loading && state.partnerCodeInput.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (state.loading) "Pairing…" else "Connect")
        }
    }
}
