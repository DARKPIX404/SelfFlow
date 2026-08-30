package com.selfflow.app.presentation.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfflow.app.R
import com.selfflow.app.presentation.viewmodel.BackupViewModel
import com.selfflow.app.presentation.viewmodel.SettingsViewModel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val wakeTime by viewModel.wakeTime.collectAsStateWithLifecycle()
    val sleepTime by viewModel.sleepTime.collectAsStateWithLifecycle()
    val dynamicThemeEnabled by viewModel.dynamicThemeEnabled.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val isPinSet by viewModel.isPinSet.collectAsStateWithLifecycle()
    val biometricAvailable = viewModel.biometricAvailable

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var showWakePicker by remember { mutableStateOf(false) }
    var showSleepPicker by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showDisableLockDialog by remember { mutableStateOf(false) }

    var titleClickCount by remember { mutableStateOf(0) }
    var showEasterEgg by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            backupViewModel.exportToUri(it) { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            backupViewModel.importFromUri(it) { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.clickable {
                            titleClickCount++
                            if (titleClickCount >= 5) {
                                titleClickCount = 0
                                showEasterEgg = true
                            }
                        }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_day_mode_section),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TimeSettingRow(
                        label = stringResource(R.string.settings_wake_time_label),
                        time = wakeTime.format(timeFormatter),
                        onClick = { showWakePicker = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    TimeSettingRow(
                        label = stringResource(R.string.settings_sleep_time_label),
                        time = sleepTime.format(timeFormatter),
                        onClick = { showSleepPicker = true }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_dynamic_theme),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = dynamicThemeEnabled,
                        onCheckedChange = { viewModel.setDynamicThemeEnabled(it) }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_data_section),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportLauncher.launch("selfflow_backup.json")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_export_button))
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.settings_import_button))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_security_section),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_app_lock_toggle),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (isPinSet) {
                                        viewModel.setAppLockEnabled(true)
                                    } else {
                                        showPinDialog = true
                                    }
                                } else {
                                    showDisableLockDialog = true
                                }
                            }
                        )
                    }

                    if (appLockEnabled && biometricAvailable) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.settings_biometric_toggle),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Switch(
                                checked = biometricEnabled,
                                onCheckedChange = { viewModel.setBiometricEnabled(it) }
                            )
                        }
                    }

                    if (appLockEnabled) {
                        OutlinedButton(
                            onClick = { showPinDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_change_pin_button))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_language_section),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Locale.getDefault().displayLanguage,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }

    if (showWakePicker) {
        val state = rememberTimePickerState(
            initialHour = wakeTime.hour,
            initialMinute = wakeTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showWakePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setWakeTime(LocalTime.of(state.hour, state.minute))
                        showWakePicker = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWakePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.settings_wake_time_picker_title)) },
            text = { TimePicker(state = state) }
        )
    }

    if (showSleepPicker) {
        val state = rememberTimePickerState(
            initialHour = sleepTime.hour,
            initialMinute = sleepTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showSleepPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setSleepTime(LocalTime.of(state.hour, state.minute))
                        showSleepPicker = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSleepPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.settings_sleep_time_picker_title)) },
            text = { TimePicker(state = state) }
        )
    }

    if (showPinDialog) {
        PinCreationDialog(
            onDismiss = { showPinDialog = false },
            onPinSet = { pin ->
                viewModel.setPin(pin)
                showPinDialog = false
            }
        )
    }

    if (showDisableLockDialog) {
        AlertDialog(
            onDismissRequest = { showDisableLockDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setAppLockEnabled(false)
                        showDisableLockDialog = false
                    }
                ) {
                    Text(stringResource(R.string.disable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableLockDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.settings_disable_lock_title)) },
            text = { Text(stringResource(R.string.settings_disable_lock_message)) }
        )
    }

    if (showEasterEgg) {
        AlertDialog(
            onDismissRequest = { showEasterEgg = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://disk.yandex.ru/d/cuN7AhA5rKcm8w")
                    }
                ) {
                    Text(stringResource(R.string.easter_egg_yandex_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEasterEgg = false }) {
                    Text(stringResource(R.string.easter_egg_close))
                }
            },
            title = { Text(stringResource(R.string.easter_egg_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        painter = painterResource(R.drawable.easter_egg_photo),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = stringResource(R.string.easter_egg_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun TimeSettingRow(
    label: String,
    time: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = time,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Button(onClick = onClick) {
            Text(stringResource(R.string.settings_change_button))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinCreationDialog(
    onDismiss: () -> Unit,
    onPinSet: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val pinInvalidLength = stringResource(R.string.pin_invalid_length)
    val pinMismatch = stringResource(R.string.pin_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        pin.length != PIN_LENGTH || pin.any { !it.isDigit() } -> {
                            error = pinInvalidLength
                        }
                        pin != confirmPin -> {
                            error = pinMismatch
                        }
                        else -> {
                            onPinSet(pin)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.pin_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        if (value.length <= PIN_LENGTH && value.all { it.isDigit() }) {
                            pin = value
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.pin_new_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { value ->
                        if (value.length <= PIN_LENGTH && value.all { it.isDigit() }) {
                            confirmPin = value
                            error = null
                        }
                    },
                    label = { Text(stringResource(R.string.pin_confirm_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}

private const val PIN_LENGTH = 4
