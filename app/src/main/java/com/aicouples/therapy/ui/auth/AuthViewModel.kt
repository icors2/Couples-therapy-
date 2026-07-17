package com.aicouples.therapy.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.data.model.NotificationType
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.NotificationRepository
import com.aicouples.therapy.ui.navigation.AuthDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val destination: AuthDestination = AuthDestination.Loading,
    val loading: Boolean = true,
    val error: String? = null,
    val configured: Boolean = true,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(configured = authRepository.isConfigured())
        }
        viewModelScope.launch {
            authRepository.ensureSessionRestored()
            authRepository.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        notificationRepository.startListening()
                        refreshProfile()
                        observeInvites()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _uiState.update {
                            it.copy(
                                destination = AuthDestination.Auth,
                                loading = false,
                            )
                        }
                    }
                    else -> {
                        _uiState.update { it.copy(loading = true) }
                    }
                }
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            authRepository.signInWithGoogle()
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, error = e.message ?: "Sign-in failed")
                    }
                }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            val profile = authRepository.fetchProfile()
            val dest = when {
                profile == null -> AuthDestination.Auth
                profile.relationshipId.isNullOrBlank() -> AuthDestination.Pairing
                else -> AuthDestination.Home
            }
            _uiState.update {
                it.copy(destination = dest, loading = false, error = null)
            }
        }
    }

    fun consumeDestination() {
        _uiState.update { it.copy(destination = AuthDestination.Home) }
    }

    private fun observeInvites() {
        viewModelScope.launch {
            notificationRepository.incoming.collect { notification ->
                if (notification.type == NotificationType.SESSION_INVITE) {
                    val sessionId = notification.payload["session_id"]?.toString()
                        ?.trim('"')
                    if (!sessionId.isNullOrBlank()) {
                        _uiState.update {
                            it.copy(destination = AuthDestination.SessionInvite(sessionId))
                        }
                    }
                }
            }
        }
    }
}
