package com.aicouples.therapy.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.AppNotification
import com.aicouples.therapy.data.model.ConnectionItem
import com.aicouples.therapy.data.model.MemberRole
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
    val connections: List<ConnectionItem> = emptyList(),
    val selectedRelationshipId: String? = null,
    val isStarting: Boolean = false,
    val pendingInvite: AppNotification? = null,
    val pendingSessionId: String? = null,
    val myPendingSessionId: String? = null,
    val showConsentForId: String? = null,
    val error: String? = null,
) {
    val selected: ConnectionItem?
        get() = connections.firstOrNull { it.relationship.id == selectedRelationshipId }
            ?: connections.firstOrNull()
}

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
                if (notification.type == NotificationType.PARENTAL_CONSENT_GRANTED ||
                    notification.type == NotificationType.PARTNER_PAIRED ||
                    notification.type == NotificationType.INTAKE_COMPLETED
                ) {
                    refresh()
                }
            }
        }
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
                val connections = relationshipRepository.listConnections()
                val selected = profile?.activeRelationshipId
                    ?.takeIf { id -> connections.any { it.relationship.id == id } }
                    ?: connections.firstOrNull()?.relationship?.id
                _uiState.update {
                    it.copy(
                        profile = profile,
                        connections = connections,
                        selectedRelationshipId = selected,
                        error = null,
                    )
                }
                refreshInviteState()
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Could not load home") }
            }
        }
    }

    fun selectConnection(relationshipId: String) {
        viewModelScope.launch {
            relationshipRepository.setActiveRelationship(relationshipId)
            _uiState.update { it.copy(selectedRelationshipId = relationshipId) }
            refreshInviteState()
        }
    }

    private suspend fun refreshInviteState() {
        val profile = _uiState.value.profile ?: authRepository.getProfile() ?: return
        val relationshipId = _uiState.value.selectedRelationshipId ?: return

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
            ?.takeIf { it.status == SessionStatus.PENDING && it.startedBy == profile.id }
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
            val selected = _uiState.value.selected ?: run {
                _uiState.update { it.copy(error = "Add a connection first") }
                return@launch
            }
            if (selected.needsConsent && selected.myRole == MemberRole.PARENT) {
                _uiState.update { it.copy(showConsentForId = selected.relationship.id) }
                return@launch
            }
            if (selected.needsMyIntake) {
                _uiState.update {
                    it.copy(error = "Complete your private intake before starting therapy")
                }
                return@launch
            }
            if (selected.waitingPartnerIntake) {
                _uiState.update {
                    it.copy(
                        error = "Waiting for ${selected.partner?.displayName ?: "them"} " +
                            "to finish their private intake",
                    )
                }
                return@launch
            }
            if (!selected.canStartTherapy) {
                _uiState.update {
                    it.copy(error = "Waiting for parent/guardian consent before therapy can start")
                }
                return@launch
            }
            _uiState.update { it.copy(isStarting = true, error = null) }
            when (val result = sessionRepository.startTherapy(selected.relationship.id)) {
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

    fun dismissConsent() {
        _uiState.update { it.copy(showConsentForId = null) }
    }

    fun grantConsentThenStart(onStarted: (String) -> Unit) {
        val relId = _uiState.value.showConsentForId ?: return
        viewModelScope.launch {
            when (val consent = relationshipRepository.consentParental(relId)) {
                is Result.Success -> {
                    if (!consent.data.ok) {
                        _uiState.update {
                            it.copy(
                                showConsentForId = null,
                                error = consent.data.message ?: "Consent failed",
                            )
                        }
                        return@launch
                    }
                    _uiState.update { it.copy(showConsentForId = null) }
                    refresh()
                    startTherapy(onStarted)
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(showConsentForId = null, error = consent.message)
                    }
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
                    _uiState.update { it.copy(myPendingSessionId = null, error = null) }
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
