package com.aicouples.therapy.intake

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.model.IntakeAnswers
import com.aicouples.therapy.data.model.MemberRole
import com.aicouples.therapy.data.model.RelationshipType
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.IntakeRepository
import com.aicouples.therapy.data.repository.RelationshipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IntakeUiState(
    val relationshipId: String = "",
    val relationshipType: RelationshipType = RelationshipType.COUPLES,
    val myRole: MemberRole = MemberRole.PARTNER,
    val partnerName: String = "them",
    val goals: String = "",
    val mainConcerns: String = "",
    val wantFromSessions: String = "",
    val strengths: String = "",
    val communicationWish: String = "",
    val anythingElse: String = "",
    val safetyConcern: Boolean = false,
    val safetyNote: String = "",
    val roleNotes: String = "",
    val alreadyCompleted: Boolean = false,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    val roleNotesLabel: String
        get() = when (myRole) {
            MemberRole.PARENT -> "What concerns you about your child or your relationship?"
            MemberRole.CHILD -> "What feels hard, or what do you want help with?"
            MemberRole.PARTNER -> "Anything specific about your roles together?"
        }

    val showRoleNotes: Boolean
        get() = relationshipType == RelationshipType.PARENT_CHILD
}

@HiltViewModel
class IntakeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val intakeRepository: IntakeRepository,
    private val relationshipRepository: RelationshipRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val relationshipId: String = checkNotNull(savedStateHandle["relationshipId"])

    private val _uiState = MutableStateFlow(IntakeUiState(relationshipId = relationshipId))
    val uiState: StateFlow<IntakeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val me = authRepository.getProfile()
                val rel = relationshipRepository.getRelationship(relationshipId)
                    ?: throw IllegalStateException("Connection not found")
                val partner = relationshipRepository.getPartnerProfile(rel)
                val myRole = when (me?.id) {
                    rel.partner1Id -> rel.partner1Role
                    else -> rel.partner2Role
                }
                val existing = intakeRepository.getMyIntake(relationshipId)
                _uiState.update {
                    it.copy(
                        relationshipType = rel.relationshipType,
                        myRole = myRole,
                        partnerName = partner?.displayName ?: "them",
                        alreadyCompleted = existing != null,
                        goals = existing?.answers?.goals.orEmpty(),
                        mainConcerns = existing?.answers?.mainConcerns.orEmpty(),
                        wantFromSessions = existing?.answers?.wantFromSessions.orEmpty(),
                        strengths = existing?.answers?.strengths.orEmpty(),
                        communicationWish = existing?.answers?.communicationWish.orEmpty(),
                        anythingElse = existing?.answers?.anythingElse.orEmpty(),
                        safetyConcern = existing?.answers?.safetyConcern == true,
                        safetyNote = existing?.answers?.safetyNote.orEmpty(),
                        roleNotes = existing?.answers?.roleNotes.orEmpty(),
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: "Could not load intake")
                }
            }
        }
    }

    fun onGoals(v: String) = _uiState.update { it.copy(goals = v, error = null) }
    fun onMainConcerns(v: String) = _uiState.update { it.copy(mainConcerns = v, error = null) }
    fun onWantFromSessions(v: String) = _uiState.update { it.copy(wantFromSessions = v, error = null) }
    fun onStrengths(v: String) = _uiState.update { it.copy(strengths = v, error = null) }
    fun onCommunicationWish(v: String) = _uiState.update { it.copy(communicationWish = v, error = null) }
    fun onAnythingElse(v: String) = _uiState.update { it.copy(anythingElse = v, error = null) }
    fun onSafetyConcern(v: Boolean) = _uiState.update { it.copy(safetyConcern = v, error = null) }
    fun onSafetyNote(v: String) = _uiState.update { it.copy(safetyNote = v, error = null) }
    fun onRoleNotes(v: String) = _uiState.update { it.copy(roleNotes = v, error = null) }

    fun submit(onDone: () -> Unit) {
        val state = _uiState.value
        if (state.alreadyCompleted) {
            onDone()
            return
        }
        if (state.goals.trim().length < 2 ||
            state.mainConcerns.trim().length < 2 ||
            state.wantFromSessions.trim().length < 2
        ) {
            _uiState.update {
                it.copy(error = "Please fill in goals, main concerns, and what you want from sessions")
            }
            return
        }
        if (state.showRoleNotes && state.roleNotes.trim().length < 2) {
            _uiState.update { it.copy(error = state.roleNotesLabel) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val answers = IntakeAnswers(
                goals = state.goals.trim(),
                mainConcerns = state.mainConcerns.trim(),
                wantFromSessions = state.wantFromSessions.trim(),
                strengths = state.strengths.trim(),
                communicationWish = state.communicationWish.trim(),
                anythingElse = state.anythingElse.trim(),
                safetyConcern = state.safetyConcern,
                safetyNote = if (state.safetyConcern) state.safetyNote.trim() else "",
                roleNotes = if (state.showRoleNotes) state.roleNotes.trim() else "",
            )
            when (val result = intakeRepository.submitIntake(relationshipId, answers)) {
                is Result.Success -> {
                    if (result.data.ok) {
                        _uiState.update { it.copy(isSubmitting = false, alreadyCompleted = true) }
                        onDone()
                    } else {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                error = result.data.message ?: "Could not save intake",
                            )
                        }
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false, error = result.message) }
                }
                Result.Loading -> Unit
            }
        }
    }
}
