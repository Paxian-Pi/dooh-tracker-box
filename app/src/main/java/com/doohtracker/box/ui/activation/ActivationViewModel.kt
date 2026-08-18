package com.doohtracker.box.ui.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doohtracker.box.data.manager.Identity
import com.doohtracker.box.data.repository.CampaignRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val repository: CampaignRepository
) : ViewModel() {

    private val _activationCode = MutableStateFlow("#Retin-")
    val activationCode: StateFlow<String> = _activationCode.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _location = MutableStateFlow("")
    val installationNotes: StateFlow<String> = _location.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _onActivationSuccess = MutableSharedFlow<Unit>()
    val onActivationSuccess = _onActivationSuccess.asSharedFlow()

    private val _stagedIdentities = MutableStateFlow<List<Identity>>(emptyList())
    val stagedIdentities: StateFlow<List<Identity>> = _stagedIdentities.asStateFlow()

    private val _isLocalMonitoringEnabled = MutableStateFlow(repository.deviceManager.isLocalMonitoringEnabled())
    val isLocalMonitoringEnabled: StateFlow<Boolean> = _isLocalMonitoringEnabled.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val isNotificationAccessGranted: StateFlow<Boolean> = repository.deviceManager.isLocalMonitoringEnabledFlow
        .flatMapLatest { enabled ->
            if (enabled) {
                repository.internalMediaMonitor.isPermissionGranted
            } else {
                flowOf(true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun checkInternalPermission() {
        repository.internalMediaMonitor.checkPermission()
    }

    init { refreshStagedIdentities() }

    fun onActivationCodeChange(code: String) {
        val prefix = "#Retin-"
        if (!code.startsWith(prefix)) {
            _activationCode.value = prefix
            return
        }

        val suffix = code.substring(prefix.length)
        if (suffix.length <= 4) {
            _activationCode.value = code
        }
    }

    fun onUsernameChange(name: String) {
        _username.value = name
    }

    fun onInstallationNotesChange(notes: String) {
        _location.value = notes
    }

    fun onLocalMonitoringToggle(enabled: Boolean) {
        _isLocalMonitoringEnabled.value = enabled
        repository.deviceManager.setLocalMonitoringEnabled(enabled)
    }

    private fun refreshStagedIdentities() {
        val current = repository.deviceManager.getIdentities()
        _stagedIdentities.value = current
        // If we already have identities, pull the existing location notes for the next entry
        if (current.isNotEmpty() && _location.value.isBlank()) {
            _location.value = current.first().location
        }
    }

    fun validateAndAddIdentity() {
        val prefix = "#Retin-"
        if (_activationCode.value == prefix || _username.value.isBlank()) {
            _errorMessage.value = "Site ID and Username are required"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = repository.activateDevice(
                code = _activationCode.value,
                user = _username.value,
                notes = _location.value.ifBlank { "Unspecified Location" }
            )

            _isLoading.value = false

            if (result.isSuccess) {
                refreshStagedIdentities()
                _activationCode.value = "#Retin-"
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

    fun clearHub() {
        viewModelScope.launch { _stagedIdentities.value = emptyList() }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
