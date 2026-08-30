package com.selfflow.app.presentation.screens.alarm

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfflow.app.R
import com.selfflow.app.presentation.util.PermissionHelper
import com.selfflow.app.presentation.viewmodel.AlarmViewModel
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(viewModel: AlarmViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val wakeTime by viewModel.wakeTime.collectAsStateWithLifecycle()
    val sleepTime by viewModel.sleepTime.collectAsStateWithLifecycle()
    val alarmEnabled by viewModel.alarmEnabled.collectAsStateWithLifecycle()

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val notificationLauncher = PermissionHelper.rememberNotificationPermissionLauncher { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.alarm_notification_denied_snackbar))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarm_title)) }
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimeRow(label = stringResource(R.string.alarm_wake_label), time = wakeTime.format(timeFormatter))
                    TimeRow(label = stringResource(R.string.alarm_sleep_label), time = sleepTime.format(timeFormatter))
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
                        text = if (alarmEnabled) {
                            stringResource(R.string.alarm_enabled)
                        } else {
                            stringResource(R.string.alarm_disabled)
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = alarmEnabled,
                        onCheckedChange = { viewModel.setAlarmEnabled(it) }
                    )
                }
            }

            if (!PermissionHelper.canDrawOverlays(context)) {
                OutlinedButton(
                    onClick = {
                        if (activity != null) {
                            PermissionHelper.requestOverlayPermission(activity)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.overlay_settings_error_snackbar))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.overlay_permission_button))
                }
            }

            if (!PermissionHelper.canPostNotifications(context)) {
                OutlinedButton(
                    onClick = {
                        if (activity != null && PermissionHelper.shouldShowNotificationRationale(activity)) {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.notification_rationale_snackbar))
                            }
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Text(
                        text = stringResource(R.string.notification_permission_button),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    when {
                        !PermissionHelper.canPostNotifications(context) -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.alarm_needs_notification_snackbar))
                            }
                        }
                        !PermissionHelper.canDrawOverlays(context) -> {
                            scope.launch {
                                snackbarHostState.showSnackbar(context.getString(R.string.alarm_needs_overlay_snackbar))
                            }
                        }
                        else -> viewModel.testAlarm(context)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.alarm_test_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TimeRow(label: String, time: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = time,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
