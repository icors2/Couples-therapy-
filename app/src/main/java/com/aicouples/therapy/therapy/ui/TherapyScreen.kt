package com.aicouples.therapy.therapy.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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

    if (state.showSessionEndedDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSessionEndedDialog,
            title = { Text("Session ended") },
            text = {
                Text("This session has ended. Please start a new session.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissSessionEndedDialog()
                        onLeave()
                    },
                ) {
                    Text("Return Home")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSessionEndedDialog) {
                    Text("OK")
                }
            },
        )
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
                        partnerLabel = state.partnerRoleLabel,
                        selected = state.selectedMessageId != null && state.selectedMessageId == message.id,
                        onLongPress = {
                            message.id?.let(viewModel::selectMessageForPin)
                        },
                        onDismissPinMenu = { viewModel.selectMessageForPin(null) },
                        onTogglePin = { viewModel.togglePin(message) },
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
                    IconButton(
                        onClick = viewModel::toggleSpeakLatestAi,
                        enabled = state.canSpeak,
                    ) {
                        Icon(
                            imageVector = if (state.isSpeaking) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (state.isSpeaking) {
                                "Stop speaking"
                            } else {
                                "Read latest therapist reply"
                            },
                        )
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

            state.error?.let { err ->
                Text(
                    text = err.take(180),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    partnerLabel: String,
    selected: Boolean,
    onLongPress: () -> Unit,
    onDismissPinMenu: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
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
        MessageSender.PARTNER_A -> if (isMine) "You" else partnerLabel
        MessageSender.PARTNER_B -> if (isMine) "You" else partnerLabel
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            PinActionBar(
                pinned = message.pinned,
                onTogglePin = onTogglePin,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.pinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned for AI memory",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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
                .then(
                    if (selected) {
                        Modifier.background(Color.White.copy(alpha = 0.12f))
                    } else {
                        Modifier
                    },
                )
                .combinedClickable(
                    onClick = {
                        if (selected) onDismissPinMenu()
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
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

@Composable
private fun PinActionBar(
    pinned: Boolean,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Surface(
                onClick = onTogglePin,
                shape = CircleShape,
                color = if (pinned) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                } else {
                    Color.Transparent
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (pinned) "Unpin" else "Pin",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (pinned) "Unpin" else "Pin for AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
