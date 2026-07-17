package com.aicouples.therapy.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.UserSettings
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.RelationshipRepository
import com.aicouples.therapy.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings? = null,
    val displayName: String = "",
    val email: String = "",
    val pairCode: String = "",
    val partnerName: String? = null,
    val isPaired: Boolean = false,
    val showUnpairConfirm: Boolean = false,
    val isUnpairing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val relationshipRepository: RelationshipRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = authRepository.getProfile()
            val settings = settingsRepository.getSettings()
            val partner = relationshipRepository.getPartnerProfile()
            _uiState.value = SettingsUiState(
                settings = settings,
                displayName = profile?.displayName.orEmpty(),
                email = profile?.email.orEmpty(),
                pairCode = profile?.pairCode.orEmpty(),
                partnerName = partner?.displayName,
                isPaired = profile?.relationshipId != null,
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = _uiState.value.settings ?: return@launch
            val updated = current.copy(notificationsEnabled = enabled)
            settingsRepository.updateSettings(updated)
            _uiState.value = _uiState.value.copy(settings = updated)
        }
    }

    fun requestUnpairConfirm(show: Boolean) {
        _uiState.update { it.copy(showUnpairConfirm = show, error = null) }
    }

    fun unpair(onUnpaired: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUnpairing = true, error = null) }
            when (val result = relationshipRepository.unpair()) {
                is Result.Success -> {
                    if (result.data.ok) {
                        _uiState.update {
                            it.copy(
                                isUnpairing = false,
                                showUnpairConfirm = false,
                                isPaired = false,
                                partnerName = null,
                            )
                        }
                        onUnpaired()
                    } else {
                        _uiState.update {
                            it.copy(
                                isUnpairing = false,
                                error = result.data.message ?: "Could not unpair",
                            )
                        }
                    }
                }
                is Result.Error -> {
                    _uiState.update {
                        it.copy(isUnpairing = false, error = result.message ?: "Could not unpair")
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}
