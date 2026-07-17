package com.aicouples.therapy.ui.therapy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.ai.AiRepository
import com.aicouples.therapy.data.model.ChatMessage
import com.aicouples.therapy.data.model.MessageSender
import com.aicouples.therapy.data.model.Profile
import com.aicouples.therapy.data.model.SessionStatus
import com.aicouples.therapy.data.model.TherapySession
import com.aicouples.therapy.data.repository.PartnerSlot
import com.aicouples.therapy.data.repository.RelationshipRepository
import com.aicouples.therapy.data.repository.SessionRepository
import com.aicouples.therapy.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val me: Profile? = null,
    val partner: Profile? = null,
    val mySlot: PartnerSlot = PartnerSlot.A,
    val draft: String = "",
    val sending: Boolean = false,
    val aiTyping: Boolean = false,
    val ending: Boolean = false,
    val showEndConfirm: Boolean = false,
    val error: String? = null,
    val elapsedLabel: String = "00:00",
    val handoffDone: Boolean = false,
)

@HiltViewModel
class TherapyViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val relationshipRepository: RelationshipRepository,
    private val aiRepository: AiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TherapyUiState())
    val uiState: StateFlow<TherapyUiState> = _uiState.asStateFlow()

    private var sessionId: String = ""
    private var inactivityJob: Job? = null
    private var timerJob: Job? = null
    private var aiJob: Job? = null

    fun bind(sessionId: String) {
        if (this.sessionId == sessionId && _uiState.value.session != null) return
        this.sessionId = sessionId
        viewModelScope.launch {
            val me = relationshipRepository.getMyProfile()
            val partner = relationshipRepository.getPartnerProfile()
            val session = sessionRepository.getSession(sessionId)
            val relationship = me?.relationshipId?.let { relationshipRepository.getRelationship(it) }
            val slot = if (me != null && relationship != null) {
                relationshipRepository.partnerRoleFor(me, relationship)
            } else {
                PartnerSlot.A
            }
            val messages = sessionRepository.listMessages(sessionId)
            _uiState.update {
                it.copy(
                    me = me,
                    partner = partner,
                    session = session,
                    messages = messages,
                    mySlot = slot,
                )
            }
            sessionRepository.subscribeToSession(sessionId)
            startTimer()
            resetInactivityWatch()
        }

        viewModelScope.launch {
            sessionRepository.messageEvents.collect { message ->
                if (message.sessionId != sessionId) return@collect
                _uiState.update { state ->
                    if (state.messages.any { it.id == message.id }) state
                    else state.copy(messages = state.messages + message)
                }
                if (message.senderRole != MessageSender.AI && message.senderRole != MessageSender.SYSTEM) {
                    resetInactivityWatch()
                    maybeAskAi()
                }
            }
        }

        viewModelScope.launch {
            sessionRepository.sessionEvents.collect { session ->
                if (session.id != sessionId) return@collect
                _uiState.update { it.copy(session = session) }
                if (session.status == SessionStatus.ENDED ||
                    session.status == SessionStatus.EXPIRED ||
                    session.status == SessionStatus.DECLINED
                ) {
                    inactivityJob?.cancel()
                }
            }
        }
    }

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun send() {
        val text = _uiState.value.draft.trim()
        if (text.isBlank() || _uiState.value.sending) return
        val role = when (_uiState.value.mySlot) {
            PartnerSlot.A -> MessageSender.PARTNER_A
            PartnerSlot.B -> MessageSender.PARTNER_B
        }
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, error = null, draft = "") }
            runCatching {
                sessionRepository.sendUserMessage(sessionId, text, role)
            }.onSuccess { message ->
                _uiState.update { state ->
                    val msgs = if (state.messages.any { it.id == message.id }) {
                        state.messages
                    } else {
                        state.messages + message
                    }
                    state.copy(messages = msgs, sending = false)
                }
                resetInactivityWatch()
                maybeAskAi()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(sending = false, draft = text, error = e.message)
                }
            }
        }
    }

    fun requestEnd() {
        _uiState.update { it.copy(showEndConfirm = true) }
    }

    fun dismissEndConfirm() {
        _uiState.update { it.copy(showEndConfirm = false) }
    }

    fun confirmEnd(reason: String = "manual", onFinished: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(ending = true, showEndConfirm = false, error = null) }
            runCatching {
                sessionRepository.endSession(sessionId, reason)
                val state = _uiState.value
                val relId = state.me?.relationshipId
                if (relId != null && aiRepository.hasApiKey()) {
                    aiRepository.generateSessionHandoff(
                        relationshipId = relId,
                        sessionMessages = state.messages,
                        partnerAName = partnerAName(),
                        partnerBName = partnerBName(),
                    )
                }
            }.onSuccess {
                _uiState.update { it.copy(ending = false, handoffDone = true) }
                sessionRepository.unsubscribe()
                onFinished()
            }.onFailure { e ->
                _uiState.update {
                    it.copy(ending = false, error = e.message ?: "Failed to end session")
                }
            }
        }
    }

    private fun maybeAskAi() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            delay(600)
            val state = _uiState.value
            val session = state.session ?: return@launch
            if (session.status != SessionStatus.ACTIVE) return@launch
            val relId = state.me?.relationshipId ?: return@launch
            if (!aiRepository.hasApiKey()) return@launch

            _uiState.update { it.copy(aiTyping = true) }
            runCatching {
                val completed = sessionRepository.countCompletedSessions(relId)
                aiRepository.maybeRespond(
                    relationshipId = relId,
                    sessionId = sessionId,
                    sessionMessages = _uiState.value.messages,
                    partnerAName = partnerAName(),
                    partnerBName = partnerBName(),
                    completedSessionCount = completed,
                )
            }.onSuccess { aiMessage ->
                if (aiMessage != null) {
                    _uiState.update { s ->
                        if (s.messages.any { it.id == aiMessage.id }) s
                        else s.copy(messages = s.messages + aiMessage)
                    }
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
            _uiState.update { it.copy(aiTyping = false) }
        }
    }

    private fun partnerAName(): String {
        val state = _uiState.value
        return when (state.mySlot) {
            PartnerSlot.A -> state.me?.displayName ?: "Partner A"
            PartnerSlot.B -> state.partner?.displayName ?: "Partner A"
        }
    }

    private fun partnerBName(): String {
        val state = _uiState.value
        return when (state.mySlot) {
            PartnerSlot.A -> state.partner?.displayName ?: "Partner B"
            PartnerSlot.B -> state.me?.displayName ?: "Partner B"
        }
    }

    private fun resetInactivityWatch() {
        inactivityJob?.cancel()
        inactivityJob = viewModelScope.launch {
            delay(Constants.INACTIVITY_TIMEOUT_MS)
            if (!isActive) return@launch
            val status = _uiState.value.session?.status
            if (status == SessionStatus.ACTIVE || status == SessionStatus.PENDING) {
                confirmEnd(reason = "timeout") {}
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val started = _uiState.value.session?.startedAt
                val label = formatElapsed(started)
                _uiState.update { it.copy(elapsedLabel = label) }
                delay(1000)
            }
        }
    }

    private fun formatElapsed(startedAt: String?): String {
        if (startedAt.isNullOrBlank()) return "00:00"
        return runCatching {
            val start = Instant.parse(startedAt)
            val seconds = (Clock.System.now() - start).inWholeSeconds.coerceAtLeast(0)
            val m = seconds / 60
            val s = seconds % 60
            "%02d:%02d".format(m, s)
        }.getOrDefault("00:00")
    }

    override fun onCleared() {
        sessionRepository.unsubscribe()
        super.onCleared()
    }
}
