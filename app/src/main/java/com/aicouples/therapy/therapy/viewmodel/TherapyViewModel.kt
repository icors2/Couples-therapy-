package com.aicouples.therapy.therapy.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.MemberRole
import com.aicouples.therapy.data.model.MessageSender
import com.aicouples.therapy.data.model.SessionStatus
import com.aicouples.therapy.data.model.TherapySession
import com.aicouples.therapy.data.model.UserProfile
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.MessageRepository
import com.aicouples.therapy.data.repository.RelationshipRepository
import com.aicouples.therapy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
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
    val partnerRoleLabel: String = "Partner",
    val isSending: Boolean = false,
    val isAiTyping: Boolean = false,
    val isSpeaking: Boolean = false,
    val canSpeak: Boolean = false,
    val elapsedLabel: String = "00:00",
    val showEndConfirm: Boolean = false,
    val showSessionEndedDialog: Boolean = false,
    val selectedMessageId: String? = null,
    val error: String? = null,
    val ended: Boolean = false,
)

private fun roleDisplay(role: MemberRole): String = when (role) {
    MemberRole.PARTNER -> "Partner"
    MemberRole.PARENT -> "Parent"
    MemberRole.CHILD -> "Child"
}

@HiltViewModel
class TherapyViewModel @Inject constructor(
    application: Application,
    savedStateHandle: SavedStateHandle,
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val relationshipRepository: RelationshipRepository,
) : AndroidViewModel(application) {

    private val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    private val _uiState = MutableStateFlow(TherapyUiState())
    val uiState: StateFlow<TherapyUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var typingTimeoutJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        initTts()
        bootstrap()
    }

    private fun initTts() {
        tts = TextToSpeech(getApplication()) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val me = authRepository.getProfile()
            val session = sessionRepository.getSession(sessionId)
            val relationship = session?.relationshipId?.let {
                relationshipRepository.getRelationship(it)
            } ?: relationshipRepository.getCurrentRelationship()
            val partner = relationshipRepository.getPartnerProfile(relationship)
            val myRole = when (me?.id) {
                relationship?.partner1Id -> MessageSender.PARTNER_A
                else -> MessageSender.PARTNER_B
            }
            val partnerRoleLabel = when {
                relationship == null -> "Partner"
                me?.id == relationship.partner1Id -> roleDisplay(relationship.partner2Role)
                else -> roleDisplay(relationship.partner1Role)
            }

            val messages = messageRepository.listMessages(sessionId)
            _uiState.update {
                it.copy(
                    me = me,
                    partner = partner,
                    myRole = myRole,
                    partnerRoleLabel = partnerRoleLabel,
                    session = session,
                    messages = messages,
                    canSpeak = messages.any { m -> m.sender == MessageSender.AI },
                    ended = session?.status == SessionStatus.ENDED ||
                        session?.status == SessionStatus.DECLINED ||
                        session?.status == SessionStatus.EXPIRED,
                )
            }
            startTimer(session?.startedAt)

            launch {
                messageRepository.subscribe(sessionId).collect { message ->
                    mergeMessage(message)
                }
            }

            // Fallback poll — realtime decode can miss inserts on some devices.
            launch {
                while (isActive && !_uiState.value.ended) {
                    delay(2_500)
                    refreshMessages()
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

    private suspend fun refreshMessages() {
        val remote = runCatching { messageRepository.listMessages(sessionId) }.getOrNull() ?: return
        _uiState.update { state ->
            if (remote.size == state.messages.size &&
                remote.map { it.id } == state.messages.map { it.id }
            ) {
                state
            } else {
                state.copy(
                    messages = remote,
                    isAiTyping = false,
                    canSpeak = remote.any { it.sender == MessageSender.AI },
                )
            }
        }
    }

    private suspend fun mergeMessage(message: ChatMessage) {
        _uiState.update { state ->
            val exists = state.messages.any { it.id == message.id }
            val next = if (exists) state.messages else state.messages + message
            val sorted = next.sortedBy { it.createdAt }
            state.copy(
                messages = sorted,
                isAiTyping = if (message.sender == MessageSender.AI) false else state.isAiTyping,
                canSpeak = sorted.any { it.sender == MessageSender.AI },
            )
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

    private fun armTypingTimeout() {
        typingTimeoutJob?.cancel()
        typingTimeoutJob = viewModelScope.launch {
            delay(25_000)
            _uiState.update { it.copy(isAiTyping = false) }
        }
    }

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun selectMessageForPin(messageId: String?) {
        _uiState.update { it.copy(selectedMessageId = messageId) }
    }

    fun togglePin(message: ChatMessage) {
        val id = message.id ?: return
        viewModelScope.launch {
            when (val result = messageRepository.setPinned(id, !message.pinned)) {
                is Result.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            selectedMessageId = null,
                            messages = state.messages.map {
                                if (it.id == id) result.data else it
                            },
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            selectedMessageId = null,
                            error = result.message ?: "Could not update pin",
                        )
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun send() {
        val content = _uiState.value.draft.trim()
        if (content.isEmpty() || _uiState.value.ended) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, draft = "", isAiTyping = true, error = null) }
            armTypingTimeout()
            when (
                val result = messageRepository.sendMessage(
                    sessionId = sessionId,
                    content = content,
                    senderRole = _uiState.value.myRole,
                )
            ) {
                is Result.Success -> {
                    val sent = result.data.message
                    val clearTyping = result.data.aiWaiting ||
                        result.data.aiInvokeFailed ||
                        result.data.aiRespond?.ok == false
                    _uiState.update { state ->
                        val messages = if (state.messages.any { it.id == sent.id }) {
                            state.messages
                        } else {
                            state.messages + sent
                        }
                        state.copy(
                            messages = messages,
                            isSending = false,
                            isAiTyping = if (clearTyping) false else state.isAiTyping,
                        )
                    }
                    if (clearTyping) typingTimeoutJob?.cancel()
                }
                is Result.Error -> {
                    typingTimeoutJob?.cancel()
                    val sessionClosed = looksLikeClosedSessionError(result.message)
                    if (sessionClosed) {
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                isAiTyping = false,
                                draft = content,
                                ended = true,
                                error = null,
                                showSessionEndedDialog = true,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                isAiTyping = false,
                                error = friendlySendError(result.message),
                                draft = content,
                            )
                        }
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun dismissSessionEndedDialog() {
        _uiState.update { it.copy(showSessionEndedDialog = false) }
    }

    fun toggleSpeakLatestAi() {
        val engine = tts ?: return
        if (_uiState.value.isSpeaking) {
            engine.stop()
            _uiState.update { it.copy(isSpeaking = false) }
            return
        }
        val text = _uiState.value.messages
            .lastOrNull { it.sender == MessageSender.AI }
            ?.content
            ?.trim()
            .orEmpty()
        if (text.isEmpty() || !ttsReady) return
        _uiState.update { it.copy(isSpeaking = true) }
        engine.setOnUtteranceProgressListener(
            object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    _uiState.update { it.copy(isSpeaking = false) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _uiState.update { it.copy(isSpeaking = false) }
                }
            },
        )
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "therapy-ai-latest")
    }

    fun requestEndConfirm(show: Boolean) {
        _uiState.update { it.copy(showEndConfirm = show) }
    }

    fun endSession(onEnded: () -> Unit) {
        viewModelScope.launch {
            when (val result = sessionRepository.endSession(sessionId)) {
                is Result.Success -> {
                    tts?.stop()
                    _uiState.update {
                        it.copy(
                            showEndConfirm = false,
                            ended = true,
                            isAiTyping = false,
                            isSpeaking = false,
                        )
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

    override fun onCleared() {
        typingTimeoutJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onCleared()
    }

    private companion object {
        fun looksLikeClosedSessionError(message: String?): Boolean {
            val m = message?.lowercase().orEmpty()
            if (m.isBlank()) return false
            return m.contains("row-level security") ||
                m.contains("row level security") ||
                m.contains("violates row-level security policy") ||
                (m.contains("messages") && m.contains("42501")) ||
                m.contains("session is not active") ||
                m.contains("session has ended") ||
                m.contains("session ended")
        }

        fun friendlySendError(message: String?): String {
            val m = message.orEmpty()
            // Never dump raw HTTP / JWT bodies into the UI.
            if (m.length > 180 || m.contains("Bearer ") || m.contains("http")) {
                return "Could not send message. Please try again."
            }
            return m.ifBlank { "Could not send message. Please try again." }
        }
    }
}
