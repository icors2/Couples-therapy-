package com.aicouples.therapy.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.data.model.TherapySession
import com.aicouples.therapy.data.repository.RelationshipRepository
import com.aicouples.therapy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val sessions: List<TherapySession> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val relationshipRepository: RelationshipRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val me = relationshipRepository.getMyProfile()
                val relId = me?.relationshipId ?: return@launch
                sessionRepository.listSessions(relId)
            }.onSuccess { sessions ->
                _uiState.value = HistoryUiState(sessions = sessions, loading = false)
            }.onFailure { e ->
                _uiState.value = HistoryUiState(loading = false, error = e.message)
            }
        }
    }
}
