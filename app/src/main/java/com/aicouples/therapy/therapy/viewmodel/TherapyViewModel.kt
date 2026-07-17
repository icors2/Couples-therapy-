package com.aicouples.therapy.therapy.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.MessageSender
import com.aicouples.therapy.data.model.SessionStatus
import com.aicouples.therapy.data.model.TherapySession
import com.aicouples.therapy.data.model.UserProfile
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.MessageRepository
import com.aicouples.therapy.data.repository.RelationshipRepository
import com.aicouples.therapy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class TherapyUiState(
    val session: TherapySession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val me: UserProfile? = null,
    val partner: UserProfile? = null,
    val myRole: MessageSender = MessageSender.PARTNER_A,
    val isSending: Boolean = false,
    val isAiTyping: Boolean = false,
    val elapsedLabel: String = "00:00",
    val showEndConfirm: Boolean = false,
    val error: String? = null,
    val ended: Boolean = false,
)

@HiltViewModel
class TherapyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val relationshipRepository: RelationshipRepository,
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow(TherapyUiState())
    val uiState: StateFlow<TherapyUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        bootstrap()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val me = authRepository.getProfile()
            val partner = relationshipRepository.getPartnerProfile()
            val relationship = relationshipRepository.getCurrentRelationship()
            val myRole = when (me?.id) {
                relationship?.partner1Id -> MessageSender.PARTNER_A
                else -> MessageSender.PARTNER_B
            }

            val session = sessionRepository.getSession(sessionId)
            val messages = messageRepository.listMessages(sessionId)
            _uiState.update {
                it.copy(
                    me = me,
                    partner = partner,
                    myRole = myRole,
                    session = session,
                    messages = messages,
                    ended = session?.status == SessionStatus.ENDED ||
                        session?.status == SessionStatus.DECLINED ||
                        session?.status == SessionStatus.EXPIRED,
                )
            }
            startTimer(session?.startedAt)

            launch {
                messageRepository.subscribe(sessionId).collect { message ->
                    _uiState.update { state ->
                        val exists = state.messages.any { it.id == message.id }
                        val next = if (exists) state.messages else state.messages + message
                        state.copy(
                            messages = next.sortedBy { it.createdAt },
                            isAiTyping = message.sender == MessageSender.AI,
                        )
                    }
                    if (message.sender == MessageSender.AI) {
                        delay(400)
                        _uiState.update { it.copy(isAiTyping = false) }
                    }
                }
            }

            launch {
                sessionRepository.observeSession(sessionId).collect { updated ->
                    if (updated != null) {
                        _uiState.update {
                            it.copy(
                                session = updated,
                                ended = updated.status == SessionStatus.ENDED ||
                                    updated.status == SessionStatus.EXPIRED ||
                                    updated.status == SessionStatus.DECLINED,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startTimer(startedAt: String?) {
        timerJob?.cancel()
        if (startedAt.isNullOrBlank()) return
        val start = runCatching { Instant.parse(startedAt) }.getOrNull() ?: return
        timerJob = viewModelScope.launch {
            while (isActive && !_uiState.value.ended) {
                val seconds = (Clock.System.now() - start).inWholeSeconds.coerceAtLeast(0)
                val mm = seconds / 60
                val ss = seconds % 60
                _uiState.update {
                    it.copy(elapsedLabel = "%02d:%02d".format(mm, ss))
                }
                delay(1.seconds)
            }
        }
    }

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun send() {
        val content = _uiState.value.draft.trim()
        if (content.isEmpty() || _uiState.value.ended) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, draft = "", isAiTyping = true, error = null) }
            when (
                val result = messageRepository.sendMessage(
                    sessionId = sessionId,
                    content = content,
                    senderRole = _uiState.value.myRole,
                )
            ) {
                is Result.Success -> {
                    _uiState.update { state ->
                        val messages = if (state.messages.any { it.id == result.data.id }) {
                            state.messages
                        } else {
                            state.messages + result.data
                        }
                        state.copy(messages = messages, isSending = false)
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isSending = false, isAiTyping = false, error = result.message, draft = content)
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun requestEndConfirm(show: Boolean) {
        _uiState.update { it.copy(showEndConfirm = show) }
    }

    fun endSession(onEnded: () -> Unit) {
        viewModelScope.launch {
            when (val result = sessionRepository.endSession(sessionId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(showEndConfirm = false, ended = true, isAiTyping = false)
                    }
                    onEnded()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(error = result.message, showEndConfirm = false) }
                }
                Result.Loading -> Unit
            }
        }
    }
}
