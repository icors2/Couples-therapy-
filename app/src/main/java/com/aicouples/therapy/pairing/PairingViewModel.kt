package com.aicouples.therapy.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.NotificationType
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.NotificationRepository
import com.aicouples.therapy.data.repository.RelationshipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PairingUiState(
    val myCode: String = "",
    val partnerCodeInput: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val paired: Boolean = false,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val relationshipRepository: RelationshipRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = authRepository.getProfile() ?: authRepository.ensureProfile()
            _uiState.update {
                it.copy(
                    myCode = profile.pairCode,
                    paired = profile.relationshipId != null,
                )
            }
        }

        // Waiting partner: detect when the other device completes pairing
        viewModelScope.launch {
            while (isActive && !_uiState.value.paired) {
                delay(2_000)
                markPairedIfReady()
            }
        }

        viewModelScope.launch {
            notificationRepository.subscribe().collect { notification ->
                if (notification.type == NotificationType.PARTNER_PAIRED) {
                    markPairedIfReady()
                }
            }
        }
    }

    fun onPartnerCodeChange(value: String) {
        _uiState.update { it.copy(partnerCodeInput = value.uppercase().take(6), error = null) }
    }

    fun pair() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = relationshipRepository.pairWithCode(_uiState.value.partnerCodeInput)) {
                is Result.Success -> {
                    markPairedIfReady()
                    _uiState.update { it.copy(isLoading = false, paired = true) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                Result.Loading -> Unit
            }
        }
    }

    private suspend fun markPairedIfReady() {
        val profile = runCatching { authRepository.getProfile() }.getOrNull() ?: return
        if (profile.relationshipId != null && !_uiState.value.paired) {
            _uiState.update {
                it.copy(
                    myCode = profile.pairCode,
                    paired = true,
                    isLoading = false,
                    error = null,
                )
            }
        }
    }
}
