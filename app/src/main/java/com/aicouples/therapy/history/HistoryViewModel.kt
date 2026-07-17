package com.aicouples.therapy.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.data.model.SessionStatus
import com.aicouples.therapy.data.model.TherapySession
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sessions: List<TherapySession> = emptyList(),
    val query: String = "",
    val showDeclined: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val filtered: List<TherapySession>
        get() {
            val base = sessions.filter { session ->
                when (session.status) {
                    SessionStatus.ENDED, SessionStatus.EXPIRED -> true
                    SessionStatus.DECLINED -> showDeclined
                    SessionStatus.PENDING, SessionStatus.ACTIVE -> false
                }
            }
            if (query.isBlank()) return base
            val q = query.lowercase()
            return base.filter {
                it.status.name.lowercase().contains(q) ||
                    it.startedAt.orEmpty().contains(q) ||
                    it.id.contains(q)
            }
        }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val profile = authRepository.getProfile()
            val relationshipId = profile?.activeRelationshipId ?: profile?.relationshipId
            if (relationshipId == null) {
                _uiState.value = HistoryUiState(
                    isLoading = false,
                    error = "Select a connection on Home first",
                )
                return@launch
            }
            val sessions = sessionRepository.listSessions(relationshipId)
            _uiState.update {
                it.copy(sessions = sessions, isLoading = false)
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun setShowDeclined(show: Boolean) {
        _uiState.update { it.copy(showDeclined = show) }
    }
}

fun TherapySession.historySubtitle(): String {
    val statusLabel = when (status) {
        SessionStatus.ENDED -> "ended"
        SessionStatus.EXPIRED -> "expired"
        SessionStatus.DECLINED -> "declined"
        SessionStatus.PENDING -> "invite"
        SessionStatus.ACTIVE -> "active"
    }
    val duration = when {
        durationSeconds != null -> "${durationSeconds / 60} min"
        endedAt != null ||
            status == SessionStatus.ENDED ||
            status == SessionStatus.EXPIRED ||
            status == SessionStatus.DECLINED -> "closed"
        else -> "in progress"
    }
    return "$statusLabel · $duration"
}
