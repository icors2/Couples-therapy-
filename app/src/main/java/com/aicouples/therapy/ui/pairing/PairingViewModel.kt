package com.aicouples.therapy.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.data.repository.RelationshipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairingUiState(
    val myCode: String = "",
    val partnerCodeInput: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val paired: Boolean = false,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val relationshipRepository: RelationshipRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = relationshipRepository.getMyProfile()
            _uiState.update {
                it.copy(
                    myCode = profile?.pairCode.orEmpty(),
                    paired = !profile?.relationshipId.isNullOrBlank(),
                )
            }
        }
    }

    fun onCodeChange(value: String) {
        _uiState.update {
            it.copy(partnerCodeInput = value.uppercase().take(6), error = null)
        }
    }

    fun pair() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            relationshipRepository.pairWithCode(_uiState.value.partnerCodeInput)
                .onSuccess {
                    _uiState.update { it.copy(loading = false, paired = true) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(loading = false, error = e.message ?: "Pairing failed")
                    }
                }
        }
    }
}
