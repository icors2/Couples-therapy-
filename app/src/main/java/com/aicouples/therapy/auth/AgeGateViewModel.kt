package com.aicouples.therapy.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicouples.therapy.common.Result
import com.aicouples.therapy.data.repository.AuthRepository
import com.aicouples.therapy.data.repository.RelationshipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgeGateUiState(
    val year: String = "",
    val month: String = "",
    val day: String = "",
    val confirmed: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AgeGateViewModel @Inject constructor(
    private val relationshipRepository: RelationshipRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgeGateUiState())
    val uiState: StateFlow<AgeGateUiState> = _uiState.asStateFlow()

    fun onYear(value: String) = _uiState.update { it.copy(year = value.filter { c -> c.isDigit() }.take(4), error = null) }
    fun onMonth(value: String) = _uiState.update { it.copy(month = value.filter { c -> c.isDigit() }.take(2), error = null) }
    fun onDay(value: String) = _uiState.update { it.copy(day = value.filter { c -> c.isDigit() }.take(2), error = null) }
    fun onConfirmed(value: Boolean) = _uiState.update { it.copy(confirmed = value) }

    fun submit(onDone: () -> Unit) {
        val state = _uiState.value
        if (!state.confirmed) {
            _uiState.update { it.copy(error = "Please confirm your date of birth is accurate") }
            return
        }
        val y = state.year.toIntOrNull()
        val m = state.month.toIntOrNull()
        val d = state.day.toIntOrNull()
        if (y == null || m == null || d == null || m !in 1..12 || d !in 1..31) {
            _uiState.update { it.copy(error = "Enter a valid date (YYYY / MM / DD)") }
            return
        }
        val dob = "%04d-%02d-%02d".format(y, m, d)
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            when (val result = relationshipRepository.attestAge(dob)) {
                is Result.Success -> {
                    if (result.data.ok) {
                        authRepository.getProfile()
                        _uiState.update { it.copy(isSubmitting = false) }
                        onDone()
                    } else {
                        _uiState.update {
                            it.copy(isSubmitting = false, error = result.data.message ?: "Could not save date of birth")
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
