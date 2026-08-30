package com.selfflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.data.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AuthState {
    object Locked : AuthState()
    object Unlocked : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unlocked)
    val authState: StateFlow<AuthState> = _authState

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError

    val biometricEnabled: StateFlow<Boolean> = secureStorage.isBiometricEnabled()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val biometricAvailable: Boolean = secureStorage.isBiometricAvailable()

    init {
        viewModelScope.launch {
            val shouldLock = secureStorage.isAppLockEnabled().first() && secureStorage.isPinSet()
            _authState.value = if (shouldLock) AuthState.Locked else AuthState.Unlocked
        }
    }

    fun authenticatePin(pin: String) {
        viewModelScope.launch {
            if (secureStorage.verifyPin(pin)) {
                _pinError.value = false
                _authState.value = AuthState.Unlocked
            } else {
                _pinError.value = true
            }
        }
    }

    fun authenticateBiometric() {
        viewModelScope.launch {
            val biometricAllowed = biometricEnabled.value && secureStorage.isAppLockEnabled().first()
            if (biometricAllowed) {
                _authState.value = AuthState.Unlocked
            }
        }
    }

    fun lock() {
        viewModelScope.launch {
            val shouldLock = secureStorage.isAppLockEnabled().first() && secureStorage.isPinSet()
            if (shouldLock) {
                _authState.value = AuthState.Locked
            }
        }
    }

    fun clearPinError() {
        _pinError.value = false
    }
}
