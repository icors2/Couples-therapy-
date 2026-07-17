package com.aicouples.therapy.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.AppNotification
import com.aicouples.therapy.data.model.NotificationType
import com.aicouples.therapy.data.model.UserProfile
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.NotificationRepository
import com.aicouples.therapy.data.repository.RelationshipRepository
import com.aicouples.therapy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

data class HomeUiState(
    val profile: UserProfile? = null,
    val partner: UserProfile? = null,
    val isStarting: Boolean = false,
    val pendingInvite: AppNotification? = null,
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
                    _uiState.update { it.copy(pendingInvite = notification) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val profile = authRepository.getProfile()
            val partner = relationshipRepository.getPartnerProfile()
            val unread = notificationRepository.listUnread()
            val invite = unread.firstOrNull { it.type == NotificationType.SESSION_INVITE }
            _uiState.update {
                it.copy(profile = profile, partner = partner, pendingInvite = invite, error = null)
            }
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
            val invite = _uiState.value.pendingInvite ?: return@launch
            val sessionId = invite.payload?.get("session_id")?.jsonPrimitive?.content ?: return@launch
            when (val result = sessionRepository.joinSession(sessionId)) {
                is Result.Success -> {
                    notificationRepository.markRead(invite.id)
                    _uiState.update { it.copy(pendingInvite = null) }
                    onJoined(sessionId)
                }
                is Result.Error -> _uiState.update { it.copy(error = result.message) }
                Result.Loading -> Unit
            }
        }
    }

    fun declineInvite() {
        viewModelScope.launch {
            val invite = _uiState.value.pendingInvite ?: return@launch
            val sessionId = invite.payload?.get("session_id")?.jsonPrimitive?.content ?: return@launch
            sessionRepository.declineSession(sessionId)
            notificationRepository.markRead(invite.id)
            _uiState.update { it.copy(pendingInvite = null) }
        }
    }
}
