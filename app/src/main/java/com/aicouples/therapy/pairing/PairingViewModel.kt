package com.aicouples.therapy.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.MemberRole
import com.aicouples.therapy.data.model.NotificationType
import com.aicouples.therapy.data.model.RelationshipType
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
    val relationshipType: RelationshipType = RelationshipType.COUPLES,
    val myRole: MemberRole = MemberRole.PARENT,
    val isMinor: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val paired: Boolean = false,
    val needsConsent: Boolean = false,
    val pendingConsentRelationshipId: String? = null,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val relationshipRepository: RelationshipRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private var baselineCount = 0

    init {
        viewModelScope.launch {
            val profile = authRepository.getProfile() ?: authRepository.ensureProfile()
            baselineCount = relationshipRepository.listRelationships().size
            val type = if (profile.isMinor) RelationshipType.PARENT_CHILD else RelationshipType.COUPLES
            _uiState.update {
                it.copy(
                    myCode = profile.pairCode,
                    isMinor = profile.isMinor,
                    relationshipType = type,
                    myRole = if (profile.isMinor) MemberRole.CHILD else MemberRole.PARENT,
                )
            }
        }

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

    fun onTypeSelected(type: RelationshipType) {
        if (_uiState.value.isMinor && type == RelationshipType.COUPLES) return
        _uiState.update {
            it.copy(
                relationshipType = type,
                myRole = when (type) {
                    RelationshipType.COUPLES -> MemberRole.PARTNER
                    RelationshipType.PARENT_CHILD ->
                        if (it.isMinor) MemberRole.CHILD else MemberRole.PARENT
                },
            )
        }
    }

    fun onRoleSelected(role: MemberRole) {
        if (role != MemberRole.PARENT && role != MemberRole.CHILD) return
        if (_uiState.value.isMinor && role == MemberRole.PARENT) return
        _uiState.update { it.copy(myRole = role) }
    }

    fun pair() {
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (
                val result = relationshipRepository.pairWithCode(
                    rawCode = state.partnerCodeInput,
                    relationshipType = state.relationshipType,
                    myRole = if (state.relationshipType == RelationshipType.PARENT_CHILD) {
                        state.myRole
                    } else {
                        MemberRole.PARTNER
                    },
                )
            ) {
                is Result.Success -> {
                    if (result.data.ok) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                paired = true,
                                needsConsent = result.data.needsParentalConsent,
                                pendingConsentRelationshipId = result.data.relationshipId,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, error = result.data.message ?: "Could not connect")
                        }
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                Result.Loading -> Unit
            }
        }
    }

    private suspend fun markPairedIfReady() {
        val count = runCatching { relationshipRepository.listRelationships().size }.getOrNull() ?: return
        if (count > baselineCount && !_uiState.value.paired) {
            _uiState.update {
                it.copy(paired = true, isLoading = false, error = null)
            }
        }
    }
}
