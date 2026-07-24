package com.doohtracker.box.ui.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doohtracker.box.data.repository.CampaignRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val repository: CampaignRepository
) : ViewModel() {

    private val _activationCode = MutableStateFlow("")
    val activationCode: StateFlow<String> = _activationCode.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _installationNotes = MutableStateFlow("")
    val installationNotes: StateFlow<String> = _installationNotes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _onActivationSuccess = MutableSharedFlow<Unit>()
    val onActivationSuccess = _onActivationSuccess.asSharedFlow()

    fun onActivationCodeChange(code: String) {
        _activationCode.value = code
    }

    fun onUsernameChange(name: String) {
        _username.value = name
    }

    fun onInstallationNotesChange(notes: String) {
        _installationNotes.value = notes
    }

    fun activateDevice() {
        if (_activationCode.value.isBlank() || _username.value.isBlank()) {
            _errorMessage.value = "Please fill in all required fields"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = repository.activateDevice(
                code = _activationCode.value,
                user = _username.value,
                notes = _installationNotes.value
            )

            _isLoading.value = false

            if (result.isSuccess) {
                _onActivationSuccess.emit(Unit)
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Activation failed"
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
