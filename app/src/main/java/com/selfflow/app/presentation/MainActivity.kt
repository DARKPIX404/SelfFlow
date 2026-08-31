package com.selfflow.app.presentation

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.selfflow.app.R
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.presentation.navigation.AppNavigation
import com.selfflow.app.presentation.navigation.Screen
import com.selfflow.app.presentation.screens.lock.LockScreen
import com.selfflow.app.presentation.theme.SelfFlowTheme
import com.selfflow.app.presentation.viewmodel.AuthState
import com.selfflow.app.presentation.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var isReady by mutableStateOf(false)
    private var onboardingCompleted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition { !isReady }

        lifecycleScope.launch {
            onboardingCompleted = settingsRepository.onboardingCompleted.first()
            isReady = true
        }

        setContent {
            if (!isReady) return@setContent

            val dynamicThemeEnabled by settingsRepository.dynamicThemeEnabled
                .collectAsStateWithLifecycle(initialValue = true)

            SelfFlowTheme(dynamicColor = dynamicThemeEnabled) {
                if (!onboardingCompleted) {
                    AppNavigation(startDestination = Screen.Onboarding.route)
                } else {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val authState by authViewModel.authState.collectAsStateWithLifecycle()
                    val biometricEnabled by authViewModel.biometricEnabled.collectAsStateWithLifecycle()
                    val pinError by authViewModel.pinError.collectAsStateWithLifecycle()

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
