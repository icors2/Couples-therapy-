package com.aicouples.therapy.ui.therapy

import androidx.lifecycle.ViewModel
import com.aicouples.therapy.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SessionInviteViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    suspend fun join(sessionId: String) {
        sessionRepository.respondToSession(sessionId, accept = true)
    }

    suspend fun decline(sessionId: String) {
        sessionRepository.respondToSession(sessionId, accept = false)
    }
}
