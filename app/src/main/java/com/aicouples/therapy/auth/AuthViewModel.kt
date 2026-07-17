package com.aicouples.therapy.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.UserProfile
import com.aicouples.therapy.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.auth.status.SessionStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val isPaired: Boolean = false,
    val profile: UserProfile? = null,
    val error: String? = null,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val profile = runCatching { authRepository.ensureProfile() }.getOrNull()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isAuthenticated = true,
                                profile = profile,
                                isPaired = profile?.relationshipId != null,
                                error = null,
                            )
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _uiState.update {
                            AuthUiState(isLoading = false, isAuthenticated = false)
                        }
                    }
                    else -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = authRepository.signInWithGoogle(activityContext)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAuthenticated = true,
                            profile = result.data,
                            isPaired = result.data.relationshipId != null,
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            val profile = authRepository.getProfile()
            _uiState.update {
                it.copy(
                    profile = profile,
                    isPaired = profile?.relationshipId != null,
                )
            }
        }
    }
}
