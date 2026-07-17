package com.aicouples.therapy.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.data.model.UserSettings
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings? = null,
    val displayName: String = "",
    val email: String = "",
    val pairCode: String = "",
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = authRepository.getProfile()
            val settings = settingsRepository.getSettings()
            _uiState.value = SettingsUiState(
                settings = settings,
                displayName = profile?.displayName.orEmpty(),
                email = profile?.email.orEmpty(),
                pairCode = profile?.pairCode.orEmpty(),
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

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onSignedOut()
        }
    }
}
