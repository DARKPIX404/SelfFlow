package com.selfflow.app.presentation

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfflow.app.R
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.presentation.navigation.AppNavigation
import com.selfflow.app.presentation.screens.lock.LockScreen
import com.selfflow.app.presentation.theme.SelfFlowTheme
import com.selfflow.app.presentation.viewmodel.AuthState
import com.selfflow.app.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val authViewModel: AuthViewModel = hiltViewModel()
            val authState by authViewModel.authState.collectAsStateWithLifecycle()
            val biometricEnabled by authViewModel.biometricEnabled.collectAsStateWithLifecycle()
            val pinError by authViewModel.pinError.collectAsStateWithLifecycle()
            val dynamicThemeEnabled by settingsRepository.dynamicThemeEnabled
                .collectAsStateWithLifecycle(initialValue = true)

            SelfFlowTheme(dynamicColor = dynamicThemeEnabled) {
                BackHandler(enabled = authState == AuthState.Locked) {
                    // Block system back while the app is locked.
                }

                when (authState) {
                    AuthState.Locked -> {
                        LockScreen(
                            onPinSubmit = { authViewModel.authenticatePin(it) },
                            pinError = pinError,
                            onPinErrorShown = { authViewModel.clearPinError() },
                            showBiometric = biometricEnabled && authViewModel.biometricAvailable,
                            onBiometricClick = { showBiometricPrompt(authViewModel) }
                        )
                    }
                    AuthState.Unlocked -> AppNavigation()
                }
            }
        }
    }

    private fun showBiometricPrompt(authViewModel: AuthViewModel) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    authViewModel.authenticateBiometric()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText(getString(R.string.cancel))
            .build()

        prompt.authenticate(promptInfo)
    }
}
