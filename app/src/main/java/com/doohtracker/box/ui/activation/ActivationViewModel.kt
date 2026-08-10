package com.doohtracker.box.ui.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doohtracker.box.data.manager.Identity
import com.doohtracker.box.data.repository.CampaignRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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

    private val _stagedIdentities = MutableStateFlow<List<Identity>>(emptyList())
    val stagedIdentities: StateFlow<List<Identity>> = _stagedIdentities.asStateFlow()

    init {
        refreshStagedIdentities()
    }

    fun onActivationCodeChange(code: String) {
        _activationCode.value = code
    }

    fun onUsernameChange(name: String) {
        _username.value = name
    }

    fun onInstallationNotesChange(notes: String) {
        _installationNotes.value = notes
    }

    private fun refreshStagedIdentities() {
        val current = repository.deviceManager.getIdentities()
        _stagedIdentities.value = current
        // If we already have identities, pull the existing location notes for the next entry
        if (current.isNotEmpty() && _installationNotes.value.isBlank()) {
            _installationNotes.value = current.first().installationNotes
        }
    }

    fun validateAndAddIdentity() {
        if (_activationCode.value.isBlank() || _username.value.isBlank()) {
            _errorMessage.value = "Site ID and Username are required"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = repository.activateDevice(
                code = _activationCode.value,
                user = _username.value,
                notes = _installationNotes.value.ifBlank { "Unspecified Location" }
            )

            _isLoading.value = false

            if (result.isSuccess) {
                refreshStagedIdentities()
                _activationCode.value = ""
                _username.value = ""
                _errorMessage.value = null
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Validation failed"
            }
        }
    }

    fun finishActivation() {
        viewModelScope.launch {
            if (_stagedIdentities.value.isNotEmpty()) {
                _onActivationSuccess.emit(Unit)
            } else {
                _errorMessage.value = "Please add at least one Site ID"
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
