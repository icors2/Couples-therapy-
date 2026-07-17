package com.aicouples.therapy.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.data.model.TherapySession
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sessions: List<TherapySession> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val filtered: List<TherapySession>
        get() {
            if (query.isBlank()) return sessions
            val q = query.lowercase()
            return sessions.filter {
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
            val relationshipId = authRepository.getProfile()?.relationshipId
            if (relationshipId == null) {
                _uiState.value = HistoryUiState(isLoading = false, error = "No relationship yet")
                return@launch
            }
            val sessions = sessionRepository.listSessions(relationshipId)
            _uiState.value = HistoryUiState(sessions = sessions, isLoading = false)
        }
    }

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }
}
