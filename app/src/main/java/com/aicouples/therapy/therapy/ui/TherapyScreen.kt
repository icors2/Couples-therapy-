package com.aicouples.therapy.therapy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.MessageSender
import com.aicouples.therapy.therapy.viewmodel.TherapyViewModel
import com.aicouples.therapy.ui.theme.AiTherapistColor
import com.aicouples.therapy.ui.theme.PartnerAColor
import com.aicouples.therapy.ui.theme.PartnerBColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TherapyScreen(
    sessionId: String,
    onLeave: () -> Unit,
    viewModel: TherapyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    if (state.showEndConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.requestEndConfirm(false) },
            title = { Text("End session?") },
            text = { Text("The AI will generate a therapeutic memory handoff, then the session will lock.") },
            confirmButton = {
                TextButton(onClick = { viewModel.endSession(onLeave) }) {
                    Text("End Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.requestEndConfirm(false) }) {
                    Text("Keep talking")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = listOfNotNull(state.me?.displayName, state.partner?.displayName)
                                .joinToString(" & ")
                                .ifBlank { "Therapy Session" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.elapsedLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.requestEndConfirm(true) }) {
                        Text("End")
                    }
                    IconButton(onClick = { /* future menu */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                },
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items(state.messages, key = { it.id ?: it.hashCode() }) { message ->
                    MessageBubble(
                        message = message,
                        isMine = message.sender == state.myRole,
                    )
                }
                item {
                    AnimatedVisibility(
                        visible = state.isAiTyping,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        Text(
                            text = "Therapist is typing…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        )
                    }
                }
            }

            if (!state.ended) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    IconButton(onClick = { /* voice placeholder */ }) {
                        Icon(Icons.Default.MicNone, contentDescription = "Voice (coming soon)")
                    }
                    OutlinedTextField(
                        value = state.draft,
                        onValueChange = viewModel::onDraftChange,
                        placeholder = { Text("Message…") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4,
                    )
                    IconButton(
                        onClick = viewModel::send,
                        enabled = state.draft.isNotBlank() && !state.isSending,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TextButton(onClick = onLeave) {
                        Text("Return Home")
                    }
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
) {
    val bubbleColor = when (message.sender) {
        MessageSender.PARTNER_A -> PartnerAColor
        MessageSender.PARTNER_B -> PartnerBColor
        MessageSender.AI -> AiTherapistColor
        MessageSender.SYSTEM -> MaterialTheme.colorScheme.surfaceVariant
    }
    val horizontalAlignment = when {
        message.sender == MessageSender.AI || message.sender == MessageSender.SYSTEM ->
            Alignment.CenterHorizontally
        isMine -> Alignment.End
        else -> Alignment.Start
    }
    val label = when (message.sender) {
        MessageSender.AI -> "Therapist"
        MessageSender.SYSTEM -> "System"
        MessageSender.PARTNER_A -> if (isMine) "You" else "Partner"
        MessageSender.PARTNER_B -> if (isMine) "You" else "Partner"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (message.sender == MessageSender.SYSTEM) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        bubbleColor
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = message.content,
                color = if (message.sender == MessageSender.SYSTEM) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.White
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        message.createdAt?.let { ts ->
            Text(
                text = ts.substringAfter('T').take(5),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
