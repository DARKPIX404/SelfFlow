package com.selfflow.app.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.selfflow.app.R
import com.selfflow.app.data.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun exportToUri(uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val message = backupManager.exportToUri(uri).fold(
                onSuccess = { count -> context.getString(R.string.export_success, count) },
                onFailure = { context.getString(R.string.export_error, it.localizedMessage ?: it.message ?: "") }
            )
            onComplete(message)
        }
    }

    fun importFromUri(uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            val message = backupManager.importFromUri(uri).fold(
                onSuccess = { count -> context.getString(R.string.import_success, count) },
                onFailure = { context.getString(R.string.import_error, it.localizedMessage ?: it.message ?: "") }
            )
            onComplete(message)
        }
    }
}
