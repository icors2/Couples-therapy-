package com.aicouples.therapy.ui.therapy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.MessageSender
import com.aicouples.therapy.data.repository.PartnerSlot
import com.aicouples.therapy.ui.theme.ChatColors

@Composable
fun TherapyScreen(
    sessionId: String,
    onEnded: () -> Unit,
    viewModel: TherapyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        viewModel.bind(sessionId)
    }

    LaunchedEffect(state.messages.size, state.aiTyping) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex + if (state.aiTyping) 1 else 0)
        }
    }

    if (state.showEndConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEndConfirm,
            title = { Text("End session?") },
            text = { Text("We'll lock this conversation and update your therapeutic memory.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmEnd(onFinished = onEnded) }) {
                    Text("End session")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEndConfirm) {
                    Text("Keep talking")
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = listOfNotNull(state.me?.displayName, state.partner?.displayName)
                        .joinToString(" & ")
                        .ifBlank { "Therapy session" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = state.elapsedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("End session") },
                        onClick = {
                            menuOpen = false
                            viewModel.requestEnd()
                        },
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    mySlot = state.mySlot,
                    myId = state.me?.id,
                )
            }
            item {
                AnimatedVisibility(
                    visible = state.aiTyping,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = "AI Therapist is typing…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChatColors.Ai,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    )
                }
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (state.ending) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.size(12.dp))
                Text("Saving therapeutic memory…")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { /* microphone placeholder */ }) {
                Icon(Icons.Outlined.MicNone, contentDescription = "Voice (coming soon)")
            }
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message…") },
                maxLines = 4,
            )
            IconButton(
                onClick = viewModel::send,
                enabled = state.draft.isNotBlank() && !state.sending,
            ) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    mySlot: PartnerSlot,
    myId: String?,
) {
    val isMine = message.senderId != null && message.senderId == myId
    val isSystem = message.senderRole == MessageSender.SYSTEM
    val bubbleColor = when (message.senderRole) {
        MessageSender.PARTNER_A -> ChatColors.PartnerA
        MessageSender.PARTNER_B -> ChatColors.PartnerB
        MessageSender.AI -> ChatColors.Ai
        MessageSender.SYSTEM -> Color.Transparent
    }
    val alignment = when {
        isSystem -> Alignment.Center
        message.senderRole == MessageSender.AI -> Alignment.CenterStart
        isMine -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        if (isSystem) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = ChatColors.System,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = when (message.senderRole) {
                        MessageSender.AI -> "AI Therapist"
                        MessageSender.PARTNER_A -> if (mySlot == PartnerSlot.A && isMine) "You" else "Partner A"
                        MessageSender.PARTNER_B -> if (mySlot == PartnerSlot.B && isMine) "You" else "Partner B"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
            }
        }
    }
}
