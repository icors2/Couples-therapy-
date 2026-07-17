package com.aicouples.therapy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.data.model.AppNotification
import com.aicouples.therapy.data.model.NotificationType
import com.aicouples.therapy.data.model.Profile
import com.aicouples.therapy.data.model.SessionStatus
import com.aicouples.therapy.data.model.TherapySession
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

data class HomeUiState(
    val me: Profile? = null,
    val partner: Profile? = null,
    val activeSession: TherapySession? = null,
    val pendingInvite: AppNotification? = null,
    val loading: Boolean = true,
    val starting: Boolean = false,
    val error: String? = null,
    val needsPairing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val relationshipRepository: RelationshipRepository,
    private val sessionRepository: SessionRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            notificationRepository.incoming.collect { n ->
                if (n.type == NotificationType.SESSION_INVITE) {
                    _uiState.update { it.copy(pendingInvite = n) }
                }
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                val me = relationshipRepository.getMyProfile()
                if (me?.relationshipId.isNullOrBlank()) {
                    _uiState.update { it.copy(needsPairing = true, loading = false) }
                    return@launch
                }
                val partner = relationshipRepository.getPartnerProfile()
                val active = sessionRepository.getActiveOrPendingSession(me!!.relationshipId!!)
                val unread = notificationRepository.listUnread()
                val invite = unread.firstOrNull { it.type == NotificationType.SESSION_INVITE }
                _uiState.update {
                    it.copy(
                        me = me,
                        partner = partner,
                        activeSession = active,
                        pendingInvite = invite,
                        loading = false,
                        needsPairing = false,
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(loading = false, error = e.message)
                }
            }
        }
    }

    fun startTherapy(onStarted: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(starting = true, error = null) }
            runCatching {
                val existing = _uiState.value.activeSession
                if (existing != null && existing.status == SessionStatus.ACTIVE) {
                    onStarted(existing.id)
                    return@runCatching existing
                }
                if (existing != null && existing.status == SessionStatus.PENDING) {
                    onStarted(existing.id)
                    return@runCatching existing
                }
                sessionRepository.startSession()
            }.onSuccess { session ->
                _uiState.update { it.copy(starting = false, activeSession = session) }
                onStarted(session.id)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(starting = false, error = e.message ?: "Could not start session")
                }
            }
        }
    }

    fun inviteSessionId(): String? =
        _uiState.value.pendingInvite?.payload?.get("session_id")?.toString()?.trim('"')
}
