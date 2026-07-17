package com.aicouples.therapy.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.AppNotification
import com.aicouples.therapy.data.model.NotificationType
import com.aicouples.therapy.data.model.SessionStatus
import com.aicouples.therapy.data.model.UserProfile
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.NotificationRepository
import com.aicouples.therapy.data.repository.RelationshipRepository
import com.aicouples.therapy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class HomeUiState(
    val profile: UserProfile? = null,
    val partner: UserProfile? = null,
    val isStarting: Boolean = false,
    val pendingInvite: AppNotification? = null,
    val pendingSessionId: String? = null,
    /** Pending invite I started — can cancel without waiting for the 30m auto-expire. */
    val myPendingSessionId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val relationshipRepository: RelationshipRepository,
    private val sessionRepository: SessionRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            notificationRepository.subscribe().collect { notification ->
                if (notification.type == NotificationType.SESSION_INVITE) {
                    val sessionId = notification.sessionId()
                    _uiState.update {
                        it.copy(
                            pendingInvite = notification,
                            pendingSessionId = sessionId ?: it.pendingSessionId,
                        )
                    }
                }
            }
        }
        // Poll so Join Therapy appears even if realtime notification decode fails.
        viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                refreshInviteState()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching {
                val profile = authRepository.getProfile()
                val partner = relationshipRepository.getPartnerProfile()
                _uiState.update {
                    it.copy(profile = profile, partner = partner, error = null)
                }
                refreshInviteState()
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Could not load home") }
            }
        }
    }

    private suspend fun refreshInviteState() {
        val profile = _uiState.value.profile ?: authRepository.getProfile() ?: return
        val relationshipId = profile.relationshipId ?: return

        val unread = runCatching { notificationRepository.listUnread() }.getOrElse { emptyList() }
        val invite = unread.firstOrNull { it.type == NotificationType.SESSION_INVITE }

        val pendingSession = runCatching {
            sessionRepository.getActiveOrPendingSession(relationshipId)
        }.getOrNull()

        val inviteSessionId = invite?.sessionId()
        val sessionStartedByPartner = pendingSession != null &&
            pendingSession.startedBy != profile.id &&
            (pendingSession.status == SessionStatus.PENDING || pendingSession.status == SessionStatus.ACTIVE)

        val pendingSessionId = when {
            inviteSessionId != null -> inviteSessionId
            sessionStartedByPartner -> pendingSession.id
            else -> null
        }

        val myPendingSessionId = pendingSession
            ?.takeIf {
                it.status == SessionStatus.PENDING && it.startedBy == profile.id
            }
            ?.id

        _uiState.update {
            it.copy(
                profile = profile,
                pendingInvite = invite,
                pendingSessionId = pendingSessionId,
                myPendingSessionId = myPendingSessionId,
            )
        }
    }

    fun startTherapy(onStarted: (String) -> Unit) {
        viewModelScope.launch {
            val relationshipId = _uiState.value.profile?.relationshipId
            if (relationshipId == null) {
                _uiState.update { it.copy(error = "Pair with a partner first") }
                return@launch
            }
            _uiState.update { it.copy(isStarting = true, error = null) }
            when (val result = sessionRepository.startTherapy(relationshipId)) {
                is Result.Success -> {
                    val sessionId = result.data.sessionId
                    _uiState.update { it.copy(isStarting = false) }
                    if (sessionId != null) onStarted(sessionId)
                    else _uiState.update { it.copy(error = result.data.message ?: "Could not start session") }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isStarting = false, error = result.message) }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun joinInvite(onJoined: (String) -> Unit) {
        viewModelScope.launch {
            val sessionId = _uiState.value.pendingSessionId
                ?: _uiState.value.pendingInvite?.sessionId()
                ?: return@launch
            when (val result = sessionRepository.joinSession(sessionId)) {
                is Result.Success -> {
                    _uiState.value.pendingInvite?.id?.let { notificationRepository.markRead(it) }
                    _uiState.update { it.copy(pendingInvite = null, pendingSessionId = null) }
                    onJoined(sessionId)
                }
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun declineInvite() {
        viewModelScope.launch {
            val sessionId = _uiState.value.pendingSessionId
                ?: _uiState.value.pendingInvite?.sessionId()
                ?: return@launch
            sessionRepository.declineSession(sessionId)
            _uiState.value.pendingInvite?.id?.let { notificationRepository.markRead(it) }
            _uiState.update { it.copy(pendingInvite = null, pendingSessionId = null) }
        }
    }

    fun cancelMyInvite() {
        viewModelScope.launch {
            val sessionId = _uiState.value.myPendingSessionId ?: return@launch
            when (val result = sessionRepository.declineSession(sessionId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(myPendingSessionId = null, error = null)
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(error = result.message ?: "Could not cancel invite") }
                }
                Result.Loading -> Unit
            }
        }
    }
}

private fun AppNotification.sessionId(): String? {
    val element = payload?.get("session_id") ?: return null
    return (element as? JsonPrimitive)?.contentOrNull
        ?: runCatching { element.jsonPrimitive.content }.getOrNull()
}
