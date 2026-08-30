package com.selfflow.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfflow.app.data.repository.SettingsRepository
import com.selfflow.app.presentation.navigation.AppNavigation
import com.selfflow.app.presentation.theme.SelfFlowTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dynamicThemeEnabled by settingsRepository.dynamicThemeEnabled
                .collectAsStateWithLifecycle(initialValue = true)

            SelfFlowTheme(dynamicColor = dynamicThemeEnabled) {
                AppNavigation()
            }
        }
    }
}
