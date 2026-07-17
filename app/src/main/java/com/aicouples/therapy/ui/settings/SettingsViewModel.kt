package com.aicouples.therapy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.BuildConfig
import com.aicouples.therapy.data.local.SettingsDataStore
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val openAiKeyInput: String = "",
    val hasStoredKey: Boolean = false,
    val hasBuildKey: Boolean = false,
    val model: String = Constants.DEFAULT_AI_MODEL,
    val email: String = "",
    val displayName: String = "",
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = settingsDataStore.openAiApiKey.first()
            val model = settingsDataStore.preferredModel.first() ?: Constants.DEFAULT_AI_MODEL
            val profile = authRepository.fetchProfile()
            _uiState.update {
                it.copy(
                    hasStoredKey = !stored.isNullOrBlank(),
                    hasBuildKey = BuildConfig.OPENAI_API_KEY.isNotBlank(),
                    model = model,
                    email = profile?.email.orEmpty(),
                    displayName = profile?.displayName.orEmpty(),
                )
            }
        }
    }

    fun onKeyChange(value: String) {
        _uiState.update { it.copy(openAiKeyInput = value, message = null, error = null) }
    }

    fun onModelChange(value: String) {
        _uiState.update { it.copy(model = value) }
    }

    fun save() {
        viewModelScope.launch {
            runCatching {
                val key = _uiState.value.openAiKeyInput.trim()
                if (key.isNotBlank()) {
                    settingsDataStore.setOpenAiApiKey(key)
                }
                settingsDataStore.setPreferredModel(_uiState.value.model.trim().ifBlank {
                    Constants.DEFAULT_AI_MODEL
                })
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        hasStoredKey = it.openAiKeyInput.isNotBlank() || it.hasStoredKey,
                        openAiKeyInput = "",
                        message = "Settings saved",
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            settingsDataStore.clearOpenAiApiKey()
            _uiState.update {
                it.copy(hasStoredKey = false, message = "API key cleared")
            }
        }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.signOut()
            onDone()
        }
    }
}
